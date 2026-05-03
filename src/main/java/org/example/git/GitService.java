package org.example.git;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.*;

import java.io.File;
import java.io.IOException;

/**
 * GitService: wraps JGit and GitHub API operations needed by the application.
 */
public class GitService {

    private final File repoDir;
    private final String githubUsername;
    private final String githubToken;

    private Git git; // JGit handle
    private UsernamePasswordCredentialsProvider credentialsProvider;

    public GitService(File repoDir, String githubUsername, String githubToken) throws IOException {
        this.repoDir = repoDir;
        this.githubUsername = githubUsername;
        this.githubToken = githubToken;
        this.credentialsProvider = new UsernamePasswordCredentialsProvider(githubUsername, githubToken);
    }

    /**
     * Returns true if a .git directory exists inside repoDir.
     */
    public boolean isGitRepository() {
        File gitDir = new File(repoDir, ".git");
        return gitDir.exists() && gitDir.isDirectory();
    }

    /**
     * Initialize local git repo, create remote repo on GitHub, add remote, make initial commit and push.
     *
     * @param repoName desired GitHub repo name
     */
    public void initializeAndCreateRemote(String repoName) throws IOException, GitAPIException {
        // 1. create GitHub repository
        GitHub github = new GitHubBuilder().withOAuthToken(githubToken, githubUsername).build();
        GHRepository ghRepository;
        try {
            // Try to create; if exists, catch and use existing
            ghRepository = github.createRepository(repoName)
                    .private_(false)
                    .autoInit(false)
                    .create();
            System.out.println("Created GitHub repository: " + ghRepository.getHtmlUrl());
        } catch (IOException e) {
            // If repository already exists, get it
            System.out.println("Could not create repository (maybe exists): " + e.getMessage());
            ghRepository = github.getRepository(githubUsername + "/" + repoName);
            System.out.println("Using existing GitHub repository: " + ghRepository.getHtmlUrl());
        }

        // 2. Initialize local repo if needed
        if (!isGitRepository()) {
            InitCommand init = org.eclipse.jgit.api.Git.init();
            init.setDirectory(repoDir);
            git = init.call();
            System.out.println("Initialized local git repository at " + repoDir.getAbsolutePath());
        } else {
            git = Git.open(repoDir);
        }

        // 3. Add remote origin
        String remoteUrl = ghRepository.getHttpTransportUrl(); // HTTPS URL
        // set remote origin by editing config (JGit remoteAdd recommended)
        try {
            git.remoteAdd()
                    .setName("origin")
                    .setUri(new org.eclipse.jgit.transport.URIish(remoteUrl))
                    .call();
        } catch (Exception ex) {
            // remote may already exist; ignore
        }

        // 4. Make an initial commit (if no commits exist)
        boolean hasCommit = true;
        try {
            git.log().call().iterator().next();
        } catch (Exception ex) {
            hasCommit = false;
        }

        if (!hasCommit) {
            // Add all files and commit
            AddCommand add = git.add();
            add.addFilepattern(".").call();
            git.commit().setMessage("Initial commit").setAuthor(new PersonIdent(githubUsername, githubUsername + "@users.noreply.github.com")).call();
            // Push
            Iterable<PushResult> results = git.push().setCredentialsProvider(credentialsProvider).setRemote("origin").setPushAll().call();
            System.out.println("Pushed initial commit to " + remoteUrl);
        } else {
            System.out.println("Local repository already has commits; skipping initial commit.");
        }
    }

    /**
     * Adds all changes, commits with message, and pushes to origin.
     */
    public void addCommitAndPushAll(String commitMessage) throws GitAPIException, IOException {
        // Open repository if necessary
        if (git == null) {
            git = Git.open(repoDir);
        }

        // Stage all changes
        git.add().addFilepattern(".").setUpdate(false).call(); // add new files too
        // It's good to also run add with update true for modifications
        git.add().addFilepattern(".").setUpdate(true).call();

        // Commit
        git.commit().setMessage(commitMessage).call();

        // Push to origin
        git.push().setCredentialsProvider(credentialsProvider).call();
    }
}