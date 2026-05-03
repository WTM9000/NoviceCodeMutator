package org.example;

import org.example.model.FileModel;
import de.fraunhofer.aisec.cpg.TranslationManager;
import de.fraunhofer.aisec.cpg.sourcemodel.builtin.FileContent;
import de.fraunhofer.aisec.cpg.graph.Graph;
import de.fraunhofer.aisec.cpg.graph.Node;
import de.fraunhofer.aisec.cpg.graph.statements.Statement;
import de.fraunhofer.aisec.cpg.frontends.cxx.CXXLanguageFrontend;
import de.fraunhofer.aisec.cpg.frontends.TranslationContext;
import de.fraunhofer.aisec.cpg.processing.IFrontend;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Result;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CpgUploader: builds a CPG from C/C++ source content using Fraunhofer CPG and uploads a simplified
 * representation into Neo4j. Implements Closeable so it can be used in try-with-resources.
 *
 * Notes:
 * - This class produces a simplified node/edge representation in Neo4j:
 *   Node labels: CPGNode
 *   Node properties: id (string), type (string), code (string, optional), filename
 *   Relationships: :CFLOW or :AST (stored as relationship type)
 *
 * - The Fraunhofer CPG library has a complex graph model. For the purpose of this integration we
 *   extract basic properties from nodes (name/type/code/location) and edges between nodes as given
 *   by node.getPrevDFG() / node.getNextDFG() / node.getAstParents etc. However those specific
 *   methods differ across CPG versions. Here we rely on iterating over graph edges via Graph API.
 *
 * - Adjust imports and API usage if your CPG dependency version differs.
 *
 * - Neo4j connection parameters are taken from Main.NEO4J_* constants by default but can be overridden.
 */
public class CpgUploader implements Closeable {

    private final Driver neo4jDriver;
    private final String neo4jUri;
    private final String neo4jUser;
    private final String neo4jPassword;

    // Keep a TranslationManager / IFrontend if needed (fraunhofer CPG)
    // We will use TranslationManager to create a translation unit from content.
    // Note: depending on CPG version API may differ; adapt if necessary.
    public CpgUploader() throws Exception {
        this(Main.NEO4J_URI, Main.NEO4J_USERNAME, Main.NEO4J_PASSWORD);
    }

    public CpgUploader(String uri, String username, String password) throws Exception {
        this.neo4jUri = uri;
        this.neo4jUser = username;
        this.neo4jPassword = password;
        this.neo4jDriver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword));
    }

    /**
     * Convert FileModel into a CPG using Fraunhofer CPG and upload nodes and relationships into Neo4j.
     *
     * This method performs the following high-level steps:
     * 1. Create a temporary file with content from FileModel and feed it into the CPG frontend.
     * 2. Build the CPG graph (TranslationManager).
     * 3. Extract nodes and simple relationships and create corresponding nodes/relationships in Neo4j.
     *
     * Note: This implementation stores a simplified view of CPG nodes. For richer synchronization,
     * consider storing node properties (start/end positions, language-specific attributes) and
     * mapping original CPG node IDs to Neo4j node IDs.
     */
    public void convertAndUpload(FileModel fileModel) throws Exception {
        // 1. Write content to a temporary file because many CPG frontends expect actual files.
        Path tmp = Files.createTempFile("cpg_upload_", "_" + fileModel.getFileName());
        try {
            Files.write(tmp, fileModel.getLines());
            // 2. Build CPG
            Graph cpgGraph = buildCpg(tmp);

            // 3. Convert CPG nodes to a simple representation suitable for Neo4j
            // Map CPG node unique id -> generated Neo4j node id (we'll store mapping by cpgId property)
            Map<String, Long> createdNodes = new HashMap<>();

            try (Session session = neo4jDriver.session()) {
                // Use a transaction to create nodes and relationships
                session.writeTransaction(tx -> {
                    // Create nodes
                    for (Node node : cpgGraph.getNodes()) {
                        // Gather properties
                        String cpgId = node.getId() != null ? node.getId().toString() : UUID.randomUUID().toString();
                        String type = node.getClass().getSimpleName();
                        String code = safeString(node.getCode());
                        String name = safeString(node.getName());

                        Map<String, Object> props = new HashMap<>();
                        props.put("cpg_id", cpgId);
                        props.put("type", type);
                        props.put("code", code);
                        props.put("name", name);
                        props.put("filename", fileModel.getFileName());

                        // Create node in Neo4j
                        String createNodeCypher =
                                "CREATE (n:CPGNode $props) RETURN id(n) as nid";
                        Result r = tx.run(createNodeCypher, Values.parameters("props", props));
                        Record rec = r.single();
                        long neo4jId = rec.get("nid").asLong();
                        createdNodes.put(cpgId, neo4jId);
                    }

                    // Create relationships. We iterate edges via Graph API: node.getNeighbors() or node.getChildren()
                    // As Graph API differs across versions, we attempt generic approaches.
                    for (Node src : cpgGraph.getNodes()) {
                        String srcId = src.getId() != null ? src.getId().toString() : null;
                        if (srcId == null) {
                            continue;
                        }
                        Long srcNeo = createdNodes.get(srcId);
                        if (srcNeo == null) continue;

                        // Prefer to create AST edges (child -> parent), but CPG has different relation accessors.
                        // We'll inspect neighbors using getEdges() if present, else use getChildren() pattern.
                        Collection<? extends Node> children = getChildNodesSafely(src);

                        if (children != null) {
                            for (Node child : children) {
                                String childId = child.getId() != null ? child.getId().toString() : null;
                                if (childId == null) continue;
                                Long childNeo = createdNodes.get(childId);
                                if (childNeo == null) continue;

                                // Create relationship with type AST
                                String relCypher = "MATCH (a),(b) WHERE id(a) = $aId AND id(b) = $bId " +
                                        "CREATE (a)-[r:AST]->(b) RETURN id(r) as rid";
                                tx.run(relCypher, Values.parameters("aId", srcNeo, "bId", childNeo));
                            }
                        }
                    }

                    return null;
                });
            }
        } finally {
            // Attempt to clean up temporary file
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Build a CPG Graph from a file using Fraunhofer CPG frontends.
     * This method is written to work with common CPG APIs. If your project uses a different version,
     * adapt the creation of TranslationManager / frontends accordingly.
     */
    private Graph buildCpg(Path sourceFile) throws Exception {
        // The CPG API evolves; the following uses a TranslationManager and CXXLanguage frontend
        // to parse the file and produce a Graph. Adjust if your version differs.
        TranslationManager manager = new TranslationManager();
        // Register C/C++ frontend
        IFrontend frontend = new CXXLanguage();
        // Setup a translation context that points to the source file
        TranslationContext ctx = new TranslationContext(Collections.singletonList(sourceFile));
        // Translate
        manager.addFrontend(frontend);
        manager.run(ctx);

        // After run, retrieve constructed graph. The API may expose a global Graph or via manager.getTranslationResult()
        // Attempt several approaches; adapt to your CPG version.
        Graph graph = manager.getGraph(); // if available
        if (graph == null) {
            // Fallback: try to obtain from frontend/translation result
            try {
                graph = manager.getTranslationResult().getGraph();
            } catch (Exception e) {
                // As a last resort, throw a descriptive exception
                throw new IllegalStateException("Unable to obtain CPG Graph from TranslationManager. Check CPG API version and adjust CpgUploader.");
            }
        }
        return graph;
    }

    /**
     * Safely get child nodes for relationship extraction.
     * This method attempts several common CPG node APIs to fetch contained/child nodes.
     */
    private Collection<? extends Node> getChildNodesSafely(Node node) {
        // Prefer getChildren() if present
        try {
            // Many Node implementations have getChildren() that returns List<Node>
            java.lang.reflect.Method m = node.getClass().getMethod("getChildren");
            Object res = m.invoke(node);
            if (res instanceof Collection) {
                //noinspection unchecked
                return (Collection<? extends Node>) res;
            }
        } catch (Exception ignored) {
        }

        // Try getAstChildren
        try {
            java.lang.reflect.Method m = node.getClass().getMethod("getAstChildren");
            Object res = m.invoke(node);
            if (res instanceof Collection) {
                //noinspection unchecked
                return (Collection<? extends Node>) res;
            }
        } catch (Exception ignored) {
        }

        // Try adjacent nodes via getNeighbors or similar
        try {
            java.lang.reflect.Method m = node.getClass().getMethod("getNeighbors");
            Object res = m.invoke(node);
            if (res instanceof Collection) {
                //noinspection unchecked
                return (Collection<? extends Node>) res;
            }
        } catch (Exception ignored) {
        }

        // As last resort, return empty collection
        return Collections.emptyList();
    }

    private String safeString(Object o) {
        if (o == null) return "";
        return o.toString();
    }

    @Override
    public void close() throws IOException {
        try {
            if (neo4jDriver != null) {
                neo4jDriver.close();
            }
        } catch (Exception e) {
            throw new IOException("Failed to close Neo4j driver: " + e.getMessage(), e);
        }
    }
}
