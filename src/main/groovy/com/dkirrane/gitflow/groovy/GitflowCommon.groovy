/*
 * Copyright (C) 2014 Desmond Kirrane
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.dkirrane.gitflow.groovy

import groovy.io.FileType
import groovy.util.logging.Slf4j

import static com.dkirrane.gitflow.groovy.Constants.*
import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.ex.GitCommandException
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException

/**
 *
 */
@Slf4j
class GitflowCommon {

    static final Long EXE_TIMEOUT = 60000L
    def envp
    File repoDir

    def prefixes = [:]
    def origin
    def originURL

    String executeLocal(cmd){
        return executeLocal(cmd, false, null)
    }

    String executeLocal(cmd, ignoreExitCode) {
        return executeLocal(cmd, ignoreExitCode, null)
    }

    String executeLocal(cmd, ignoreExitCode, stdin){
        log.debug ""
        log.debug "Executing command: '${cmd}'"
        log.debug "In directory: '${repoDir}'"

        StringBuilder standard = new StringBuilder(450000)
        StringBuilder error = new StringBuilder(450000)
        def process = cmd.execute(envp, repoDir)
        process.consumeProcessOutput(standard, error)
        process.waitFor()
        def exitCode = process.exitValue()

        log.debug standard.toString()
        log.debug "Exit code: " + exitCode
        if(!ignoreExitCode){
            if (exitCode != 0){
                def msg = "Error executing command: '${cmd}'"
                def stout = standard.toString()
                def sterr = error.toString()
                if(stout.contains("Merge conflict") || stout.contains("CONFLICT")){
                    List<File> conflictedFiles = gitMergeConflicts()
                    throw new GitflowMergeConflictException(standard.toString(), conflictedFiles)
                } else{
                    throw new GitCommandException(msg, exitCode, standard.toString(), error.toString())
                }
            }
        } else {
            if(error.toString()) {
                log.warn "Executing command: '${cmd}'"
                log.warn "Exit code: " + process.exitValue()
                log.warn error.toString()
            }
        }
        return standard.toString().trim()
    }

    Integer executeRemote(cmd){
        log.debug ""
        log.debug "Executing command: '${cmd}'"
        log.debug "In directory: '${repoDir}'"

        StringBuilder standard = new StringBuilder(450000)
        StringBuilder error = new StringBuilder(450000)
        Process process = cmd.execute(envp, repoDir)

        process.consumeProcessOutput(standard, error)
        process.waitForOrKill(EXE_TIMEOUT)

        log.debug standard.toString()
        log.debug "Exit code: " + process.exitValue()

        if(error.toString()) {
            if(error.toString().startsWith("Everything up-to-date")) { // This should not be an error
                log.debug "Executing command: '${cmd}'"
                log.debug "Exit code: " + process.exitValue()
                log.debug error.toString()
            } else if(error.toString().startsWith("To ${getOriginURL()}")) { // This should not be an error
                log.debug "Executing command: '${cmd}'"
                log.debug "Exit code: " + process.exitValue()
                log.debug error.toString()
            } else {
                log.warn "Executing command: '${cmd}'"
                log.warn "Exit code: " + process.exitValue()
                log.warn error.toString()
            }
        }

        return process.exitValue()
    }

    String getOrigin() {
        if (null == origin) {
            def remotes = []
            def process = "git remote".execute(envp, repoDir)
            process.in.eachLine { line -> remotes.add(line.replaceAll("^(\\*\\s+|\\s+)", "")) }
            origin = remotes[0];
        }
        return origin
    }

    String getOriginURL() {
        if (null == originURL) {
            originURL = executeLocal("git config --get remote.origin.url", true)
        }
        return originURL
    }

    String getGitflowPrefixes(configName) {
        String configValue;
        if(prefixes?.isEmpty() || !prefixes.containsKey(configName)) {
            def key
            def value
            def process = "git config --get-regexp gitflow".execute(envp, repoDir)
            process.in.eachLine { line ->
                // If the line isn't blank
                if(line.trim()) {
                    // Split into a key and value
                    def props = line.split(' ').collect { it.trim() }
                    log.debug "${props[0]}=${props[1]}"
                    key=props[0]
                    value=props[1]
                    prefixes[key]=value ?: ""
                }
            }
        }
        log.debug "Gitflow config ${prefixes}"

        return prefixes[configName];
    }

    String getMasterBranch() {
        def master = getGitflowPrefixes('gitflow.branch.master')
        log.debug "gitflow.branch.master = ${master}"
        return master
    }

    String getDevelopBranch() {
        def develop = getGitflowPrefixes('gitflow.branch.develop')
        log.debug "gitflow.branch.develop = ${develop}"
        return develop
    }

    String getFeatureBranchPrefix() {
        def prefix = getGitflowPrefixes('gitflow.prefix.feature')
        log.debug "gitflow.prefix.feature = ${prefix}"
        return prefix
    }

    String getReleaseBranchPrefix() {
        def prefix = getGitflowPrefixes('gitflow.prefix.release')
        log.debug "gitflow.prefix.release = ${prefix}"
        return prefix
    }

    String getHotfixBranchPrefix() {
        def prefix = getGitflowPrefixes('gitflow.prefix.hotfix')
        log.debug "gitflow.prefix.hotfix = ${prefix}"
        return prefix
    }

    String getSupportBranchPrefix() {
        def prefix = getGitflowPrefixes('gitflow.prefix.support')
        log.debug "gitflow.prefix.support = ${prefix}"
        return prefix
    }

    String getVersionTagPrefix() {
        def prefix = getGitflowPrefixes('gitflow.prefix.versiontag')
        log.debug "gitflow.prefix.versiontag = ${prefix}"
        return prefix
    }

    List<File> gitMergeConflicts() {
        def mergeConflicts = []

        // http://stackoverflow.com/questions/3065650/whats-the-simplest-way-to-git-a-list-of-conflicted-files
        def process = "git diff --name-only --diff-filter=U".execute(envp, repoDir)
        process.in.eachLine { line -> mergeConflicts.add(new File(repoDir, line)) }

        return mergeConflicts;
    }

    List gitLocalBranches() {
        def localBranches = []

        def process = "git branch --no-color".execute(envp, repoDir)
        process.in.eachLine { line -> localBranches.add(line.replaceAll("^(\\*\\s+|\\s+)", "")) }

        return localBranches;
    }

    List gitRemoteBranches() {
        def remoteBranches = []

        // git branch -r --no-color
        def process = "git ls-remote --heads --refs ${getOriginURL()}".execute(envp, repoDir)
        process.in.eachLine { line -> remoteBranches.add(line.replaceAll("^.*refs/heads/", "${origin}/")) }

        return remoteBranches;
    }


    List gitAllBranches() {
        return gitLocalBranches() + gitRemoteBranches()
    }

    List gitAllTags() {
        // git tag
        def allTags = []

        def process = "git tag".execute(envp, repoDir)
        process.in.eachLine { line -> allTags.add(line.replaceAll("^(\\*\\s+|\\s+)", "")) }

        return allTags;
    }

    String gitCurrentBranch() {
        Process p = "git branch --no-color".execute(envp, repoDir)
        def localBranches = p.text.trim()

        def matcher = localBranches =~ "\\* .*"

        return matcher[0].replaceAll("^(\\*\\s+|\\s+)", "")
    }

    List gitRemoteFeatureBranches() {
        String prefix = getFeatureBranchPrefix()
        return gitRemoteBranches().findAll({ it.startsWith(prefix) })
    }

    List gitLocalFeatureBranches() {
        String prefix = getFeatureBranchPrefix()
        return gitLocalBranches().findAll({ it.startsWith(prefix) })
    }

    List gitRemoteReleaseBranches() {
        String prefix = getReleaseBranchPrefix()
        return gitRemoteBranches().findAll({ it.startsWith(prefix) })
    }

    List gitLocalReleaseBranches() {
        String prefix = getReleaseBranchPrefix()
        return gitLocalBranches().findAll({ it.startsWith(prefix) })
    }

    List gitRemoteHotfixBranches() {
        String prefix = getHotfixBranchPrefix()
        return gitRemoteBranches().findAll({ it.startsWith(prefix) })
    }

    List gitLocalHotfixBranches() {
        String prefix = getHotfixBranchPrefix()
        return gitLocalBranches().findAll({ it.startsWith(prefix) })
    }

    List gitRemoteSupportBranches() {
        String prefix = getSupportBranchPrefix()
        return gitRemoteBranches().findAll({ it.startsWith(prefix) })
    }

    List gitLocalSupportBranches() {
        String prefix = getSupportBranchPrefix()
        return gitLocalBranches().findAll({ it.startsWith(prefix) })
    }

    List gitRemoteTags() {
        // git tag
        def remoteTags = []

        def process = "git ls-remote --tags --refs --quiet".execute(envp, repoDir)
        process.in.eachLine { line -> remoteTags.add(line.replaceAll("^.*refs/tags/", "")) }

        return remoteTags;
    }

    List gitLocalTags() {
        // git tag
        def localTags = []

        def process = "git tag --sort=taggerdate".execute(envp, repoDir)
        process.in.eachLine { line -> localTags.add(line.replaceAll("^(\\*\\s+|\\s+)", "")) }

        return localTags.reverse(); // reverse here so latest tag is at index 0
    }

    Boolean gitIsCleanWorkingTree() {
        Process headProcess = "git rev-parse --verify HEAD".execute(envp, repoDir)
        def headCode =  headProcess.waitFor();
        log.debug "Verify HEAD code: " + headCode;

        // Check if any conflicts
        Process uiProcess = "git update-index -q --ignore-submodules --refresh".execute(envp, repoDir)
        def uiCode =  uiProcess.waitFor();
        log.debug "Update index code: " + uiCode;

        // Check for unstaged changes in the working tree (exit code is 0 if clean)
        Process wcProcess = "git diff --no-ext-diff --ignore-submodules --quiet --exit-code".execute(envp, repoDir)
        def wcCode =  wcProcess.waitFor();
        log.debug "Working Copy code: " + wcCode;

        // Check for uncommitted changes in the index (exit code is 0 if clean)
        Process idxProcess = "git diff-index --cached --quiet --ignore-submodules HEAD --".execute(envp, repoDir)
        def idxCode =  idxProcess.waitFor();
        log.debug "Stage code: " + idxCode;

        boolean clean = false;
        if(0 != headCode){
            clean = false
        } else if(0 != uiCode){
            clean = false
        } else if(0 != wcCode){
            clean = false
        } else if(0!=idxCode){
            clean = false
        } else{
            clean = true
        }
        return clean
    }

    Boolean gitRepoIsHeadless() {
        // ! git rev-parse --quiet --verify HEAD >/dev/null 2>&1
    }

    Boolean gitLocalBranchExists(String branchName) {
        List brnhs = this.gitLocalBranches()
        return brnhs.contains(branchName)
    }

    Boolean gitRemoteBranchExists(String branchName) {
        def isRemote = false
        def origin = getOrigin()
        if(origin) {
            if(!branchName.startsWith(origin)) {
                branchName = origin + '/' + branchName
            }
            List branches = this.gitRemoteBranches()
            if(branches.contains(branchName)){
                isRemote = true
            }
        } else {
            isRemote = false
        }
        return isRemote
    }

    Boolean gitBranchExists(String branchName) {
        def isLocal = gitLocalBranchExists(branchName)
        def isRemote = gitRemoteBranchExists(branchName)
        return (isLocal || isRemote)
    }

    Boolean gitTagExists(String tagName) {
        List tags = gitAllTags()
        return tags.contains(tagName)
    }

    /**
     * <pre>
     *
     * Tests whether branches and their "origin" counterparts have diverged and
     * need merging first. It returns error codes to provide more detail, like
     * so:
     *
     * # 0 Branch heads point to the same commit
     * # 1 First given branch needs fast-forwarding
     * # 2 Second given branch needs fast-forwarding
     * # 3 Branch needs a real merge
     * # 4 There is no merge base, i.e. the branches have no common ancestors
     *
     * </pre>
     */
    Integer gitCompareBranches(String branch1, String branch2) {
        log.debug("Comparing branches ${branch1} and ${branch2}")
        def commit1 = executeLocal("git rev-parse ${branch1}")
        def commit2 = executeLocal("git rev-parse ${branch2}")
        log.debug("Branch ${branch1} commit is ${commit1}")
        log.debug("Branch ${branch2} commit is ${commit2}")

        if (commit1 != commit2){
            def base = executeLocal("git merge-base ${commit1} ${commit2}", true)
            log.debug("Base is ${base}")
            if (null == base){
                return 4
            }
            else if (commit1 == base){
                return 1
            }
            else if (commit2 == base){
                return 2
            }
            else {
                return 3
            }
        } else{
            return 0
        }
    }

    /**
     * Checks whether branch (branch) is succesfully merged into another branch (base)
     */
    Boolean gitIsBranchMergedInto(String branch, String base) {
        def allMerges = []
        def process = "git branch --no-color --contains ${branch}".execute(envp, repoDir)
        process.in.eachLine { line -> allMerges.add(line.replaceAll("^(\\*\\s+|\\s+)", "")) }
        return allMerges.any({ it.contains(base) })
    }

    Boolean gitflowHasMasterConfigured() {
        def master = getMasterBranch()
        if((master != "") && gitLocalBranchExists(master)){
            return true
        }
        return false
    }

    Boolean gitflowHasDevelopConfigured() {
        def develop = getDevelopBranch()
        if((develop != "") && gitLocalBranchExists(develop)){
            return true
        }
        return false

    }

    Boolean gitflowHasPrefixesConfigured() {
        def feature = getFeatureBranchPrefix()
        def release = getReleaseBranchPrefix()
        def hotfix = getHotfixBranchPrefix()
        def support = getSupportBranchPrefix()
        def versiontag = getVersionTagPrefix()

        log.debug("Prefixes feature=${feature}, release=${release}, hotfix=${hotfix}, support=${support}, versiontag=${versiontag}")

        if(feature && release && hotfix && support && (versiontag != null)){
            return true
        }
        return false
    }

    Boolean gitflowIsInitialized() {
        def repoExists = repoDir.exists()
        def master = getMasterBranch()
        def develop = getDevelopBranch()
        def hasMasterConfig = gitflowHasMasterConfigured()
        def hasDevelopConfig = gitflowHasDevelopConfigured()
        def hasPrefixesConfig = gitflowHasPrefixesConfigured()

        log.debug("Gitflow config repoExists=${repoExists}, master=${master}, develop=${develop}, hasMasterConfig=${hasMasterConfig}, hasDevelopConfig=${hasDevelopConfig}, hasPrefixesConfig=${hasPrefixesConfig}")

        if(repoExists &&
            hasMasterConfig &&
            hasDevelopConfig &&
            !master.equals(develop) &&
            hasPrefixesConfig){
            return true
        }
        return false
    }

    // loading settings that can be overridden using git config
    public static void gitflowLoadSettings() {
        //        export DOT_GIT_DIR=$(git rev-parse --git-dir 2>/dev/null)
        //        export MASTER_BRANCH=$(git config --get gitflow.branch.master)
        //        export DEVELOP_BRANCH=$(git config --get gitflow.branch.develop)
        //        export ORIGIN=$(git config --get gitflow.origin || echo origin)
    }

    void gitflowResolveNameprefix() {
    }

    void requireGitRepo() throws GitflowException {
        log.debug("Verifying we are in a Git repo")
        try {
            executeLocal("git rev-parse --git-dir")
        } catch(GitflowException ex) {
            //            repoDir.eachFileRecurse (FileType.FILES) { file ->
            //                log.debug file.path
            //            }
            throw new GitflowException("Not a valid Git repo '${repoDir}' - ${ex.message}")
        }
    }

    void checkRemoteConnection() throws GitCommandException {
        log.debug("Verifying we can connect to the remote Git repo")
        def origin = getOrigin()
        if(origin) {
            StringBuilder standard = new StringBuilder(450000)
            StringBuilder error = new StringBuilder(450000)
            // @todo No way currently to check if the user has Git Hook 'access-control' privledges to the Git repo.
            // This wil test connection but also update remotes and tags
            // Fetch any new tags and prune any branches that may already be deleted
            def process = "git fetch --all --tags --prune".execute(envp, repoDir)
            process.consumeProcessOutput(standard, error)
            process.waitFor()

            String stOut = standard.toString()
            String stErr = error.toString()
            Integer exitCode = process.exitValue()

            log.debug ""
            log.debug "Checking remote connection"
            log.debug "Exit code: ${exitCode}"
            log.debug "${stOut}"
            log.debug "${stErr}"
            log.debug ""

            if(exitCode != 0) {
                def msg;
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    msg = "Issue occurred when connecting to remote Git repo '${origin}' '${getOriginURL()}'. ${PUSH_ISSUE_WIN}";
                } else {
                    msg = "Issue occurred when connecting to remote Git repo '${origin}' '${getOriginURL()}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitCommandException(msg, exitCode, stOut.toString(), stErr.toString())
            }
        }
    }

    void requireGitflowInitialized() {
    }

    void requireCleanWorkingTree() throws GitflowException {
        if(!this.gitIsCleanWorkingTree()){
            throw new GitflowException("Git Working tree contains unstaged or uncommited changes. Aborting.");
        }
    }

    void requireLocalBranch(String branchName) {
        if(!gitLocalBranchExists(branchName)){
            throw new GitflowException("ERROR: Local branch '${branchName}' does not exist and is required. Aborting.");
        }
    }

    void requireRemoteBranch(String branchName) {
        if(!gitRemoteBranchExists(branchName)) {
            throw new GitflowException("ERROR: Remote branch '${branchName}' does not exist and is required. Aborting.");
        }
    }

    void requireBranch(String branchName) {
        List branches = gitAllBranches();
        if(!branches.contains(branchName)){
            throw new GitflowException("ERROR: Branch '${branchName}' does not exist and is required. Aborting.");
        }
    }

    void requireBranchAbsent(String branchName) {
        List branches = gitAllBranches();
        if(branches.contains(branchName)){
            throw new GitflowException("ERROR: Branch '${branchName}' already exists. Aborting.");
        }
    }

    void requireTagAbsent(String tagName) {
        List tags = gitAllTags()
        if(tags.contains(tagName)){
            throw new GitflowException("ERROR: Tag '${tagName}' already exists. Aborting.");
        }
    }

    void requireBranchesEqual(String branch1, String branch2) {
        requireLocalBranch(branch1)
        requireRemoteBranch(branch2)
        Integer result = gitCompareBranches(branch1, branch2)
        if(0 != result){
            throw new GitflowException("ERROR: Branches '${branch1}' and '${branch2}' must be at the same commit. Aborting");
        }
    }

    void requireLocalBranchNotBehind(String branch1, String branch2) {
        requireLocalBranch(branch1)
        requireRemoteBranch(branch2)
        Integer result = gitCompareBranches(branch1, branch2)
        // 0 branches are equal
        // 2 remote branch2 is behind local branch1
        // If result is not 0 or 2 then branch1 must be behind branch2
        if([0, 2].contains(result) == false){
            throw new GitflowException("ERROR: Local '${branch1}' branch must not be behind remote '${branch2}' branch. Aborting");
        }
    }

    static void main(String[] args) {
        GitflowCommon common = new GitflowCommon()

        def local =  common.gitLocalBranches()
        println "\nLocal Branches:"
        local.each { log.info it }

        def remotes =  common.gitRemoteBranches()
        println "\nRemote Branches:"
        remotes.each { log.info it }

        def allBrns =  common.gitAllBranches()
        println "\nAll Branches:"
        allBrns.each { log.info it }

        def tags =  common.gitAllTags()
        println "\nAll Tags:"
        tags.each { log.info it }

        def currentBrn =  common.gitCurrentBranch()
        println "\nCurrent Branch: ${currentBrn}"

        def clean =  common.gitIsCleanWorkingTree()
        println "\nClean WorkingTree: ${clean}"
    }

}