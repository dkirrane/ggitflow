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

import static com.dkirrane.gitflow.groovy.Constants.*
import com.dkirrane.gitflow.groovy.ex.GitCommandException
import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import groovy.util.logging.Slf4j
import java.io.File

/**
 *
 */
@Slf4j
class GitflowFeature {

    @Delegate GitflowInit init

    def startCommit
    def isRebase
    def isInteractive
    def squash
    def msgPrefix
    def msgSuffix
    def push

    void start(String featureBranchName) throws GitCommandException, GitflowException {
        init.requireGitRepo()

        if(!featureBranchName) {
            throw new GitflowException("Missing argument <featureBranchName>")
        }
        if(!init.gitflowIsInitialized()){
            init.cmdDefault()
        }

        def prefix = init.getFeatureBranchPrefix()
        if(featureBranchName.contains(prefix)){
            featureBranchName = featureBranchName.minus(prefix)
        }
        def featureBranch = prefix + featureBranchName

        // require_base_is_on_develop
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(startCommit) {
            def scOnDevelop = init.gitIsBranchMergedInto(startCommit,develop)
            if(!scOnDevelop){
                throw new GitflowException("Given start commit '${startCommit}' is not a valid commit on '${develop}'.")
            }
        } else {
            startCommit = develop
        }

        // sanity checks
        if(init.gitBranchExists(featureBranch)){
            throw new GitflowException("Feature branch ${featureBranch} already exists")
        }

        // update the local repo with remote changes, if asked
        def origin = init.getOrigin()
        if(origin){
            // if the origin branch counterpart exists, fetch and assert that
            // the local branch isn't behind it (to avoid unnecessary rebasing)
            Integer exitCode = init.executeRemote("git fetch --all")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue fetching from '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue fetching from '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }

            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
        }

        // create branch
        def cmd = "git checkout -b ${featureBranch} ${develop}"
        init.executeLocal(cmd)

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push -u ${origin} ${featureBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${featureBranch}' to '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue pushing feature branch '${featureBranch}' to '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }
        }

        log.info ""
        log.info "Summary of actions:"
        log.info "- A new branch '${featureBranch}' was created, based on '${develop}'"
        log.info "- You are now on branch '${featureBranch}'"
        log.info ""
        log.info "Now, start committing on your feature. When done, use:"
        log.info ""
        log.info "     mvn gitflow:feature-finish"
        log.info ""
    }

    void finish(String featureBranchName) throws GitCommandException, GitflowMergeConflictException, GitflowException {
        init.requireGitRepo()

        if(!featureBranchName) {
            throw new GitflowException("Missing argument <featureBranchName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }
        msgPrefix = msgPrefix ? msgPrefix + " " : ""
        msgSuffix = msgSuffix ? " " + msgSuffix : ""

        def prefix = init.getFeatureBranchPrefix()
        if(featureBranchName.contains(prefix)){
            featureBranchName = featureBranchName.minus(prefix)
        }
        def featureBranch = prefix + featureBranchName

        // sanity checks
        if(!init.gitBranchExists(featureBranch)){
            throw new GitflowException("Feature branch " + featureBranch + " does not exist")
        }

        // detect if we're restoring from a merge conflict
        File mergeBaseFile = new File(init.repoDir, ".git" + File.separator + ".gitflow" + File.separator + "MERGE_BASE")
        String mergeBasePath = mergeBaseFile.getCanonicalPath()
        if(mergeBaseFile.exists()) {
            if(init.gitIsCleanWorkingTree()){
                def finishBase = init.executeLocal("cat ${mergeBasePath}")

                // Since the working tree is now clean, either the user did a
                // succesfull merge manually, or the merge was cancelled.
                // We detect this using git_is_branch_merged_into()
                if(init.gitIsBranchMergedInto(featureBranch, finishBase)) {
                    init.executeLocal("rm -f ${mergeBasePath}")
                    this.helperFinishCleanup(featureBranch)
                    System.exit(0)
                } else {
                    // If the user cancelled the merge and decided to wait until later,
                    // that's fine. But we have to acknowledge this by removing the
                    // MERGE_BASE file and continuing normal execution of the finish
                    mergeBaseFile.delete()
                }
            } else {
                log.warn ""
                log.warn "Merge conflicts not resolved yet, use:"
                log.warn "    git mergetool"
                log.warn "    git commit"
                log.warn ""
                log.warn "You can then complete the finish by running feature-finish again"
                log.warn ""
                throw new GitflowMergeConflictException("Merge conflicts not resolved yet")
            }
        }

        if(!init.gitIsCleanWorkingTree()){
            throw new GitflowException("Failed to finish. Need a clean working tree")
        }

        // update local repo with remote changes
        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        if(origin){
            // if the origin branch counterpart exists, fetch and assert that
            // the local branch isn't behind it (to avoid unnecessary rebasing)
            Integer exitCode = init.executeRemote("git fetch --all")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue fetching from '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue fetching from '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }

            if(init.gitRemoteBranchExists("${origin}/${featureBranch}")){
                init.requireBranchesEqual(featureBranch, "${origin}/${featureBranch}")
            }
            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
        }

        //  if the user wants to rebase, do that first
        if(isRebase) {
            this.rebase(featureBranch)
        }

        // merge into BASE
        init.executeLocal("git checkout ${develop}")

        def commits = []
        def process = "git rev-list -n2 ${develop}..${featureBranch}".execute(null, init.repoDir)
        process.in.eachLine { line -> commits.add(line) }

        def msg = "${msgPrefix}Merge branch '${featureBranch}' into ${develop}${msgSuffix}"
        if(commits.size() == 1) {
            init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "--ff", "${featureBranch}"])
        } else {
            if(!squash){
                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "--no-ff", "${featureBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${featureBranch}' into ${develop}${msgSuffix}"
                init.executeLocal("git merge --squash ${featureBranch}")
                init.executeLocal(["git", "commit", "-m", "\"${squashMsg}\""])

                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "${featureBranch}"])
            }
        }

        log.info ""
        log.info "Summary of actions:"
        log.info "- The feature branch '${featureBranch}' was merged into '${develop}'"
        log.info "- You are now on branch '${develop}'"
        log.info ""
    }

    private void rebase(String featureBranchName) throws GitCommandException, GitflowException {
        init.requireCleanWorkingTree()
        init.requireBranch(featureBranchName)

        init.executeLocal("git checkout -q ${featureBranchName}")

        def opts
        if(isInteractive){
            opts = "-i"
        }

        def develop = init.getDevelopBranch()

        Integer rebaseExitCode
        if(opts) {
            rebaseExitCode = init.executeRemote("git rebase ${opts} ${develop}")
        } else {
            rebaseExitCode = init.executeRemote("git rebase ${develop}")
        }

        if(rebaseExitCode != 0){
            log.warn "WARN: Finish was aborted due to conflicts during rebase."
            log.warn "WARN: Please finish the rebase manually now."
            log.warn "WARN: When finished, re-run:"
            log.warn "WARN: mvn gitflow:feature-finish"
            throw new GitflowException("Rebase feature onto develop has conflicts")
        } else {
            log.debug "Rebase complete"
        }
    }

    void publish(String featureBranch, boolean pushIt) throws GitCommandException, GitflowException {
        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        if(pushIt && origin){

            def pushing = [develop]
            for (branch in pushing) {
                pushBranch(origin, branch)
            }

            log.info "Deleting ${featureBranch}"
            init.executeLocal("git branch -D ${featureBranch}")

            // Delete remote release branch
            if(init.gitRemoteBranchExists("${origin}/${featureBranch}")){
                log.info "Deleting ${origin}/${featureBranch}"
                init.executeRemote("git push --delete ${origin} ${featureBranch}")
            }

        } else {
            log.warn "===> Once happy with the merge you MUST manually push:"
            log.warn ""
            log.warn "        git push ${origin} ${develop}"
            log.warn ""
            log.warn "===> And manually delete the feature branch:"
            log.warn ""
            log.warn "        git branch --delete ${featureBranch}"
            if(init.gitRemoteBranchExists("${origin}/${featureBranch}")){
                log.warn "        git push --delete ${origin} ${featureBranch}"
            }
            log.info ""
        }
    }

    void pushBranch(String origin, String branch) throws GitCommandException, GitflowException {
        if(!init.gitRemoteBranchExists("${origin}/${branch}")){
            log.debug "Remote branch ${branch} does not exists. Skipping push"
            return;
        }
        log.info "Pushing ${branch}"
        Integer exitCode = init.executeRemote("git push -u ${origin} ${branch}")
        if(exitCode){
            def errorMsg
            if (System.properties['os.name'].toLowerCase().contains("windows")) {
                errorMsg = "Issue pushing branch '${branch}' to '${origin}'. ${PUSH_ISSUE_WIN}"
            } else {
                errorMsg = "Issue pushing branch '${branch}' to '${origin}'. ${PUSH_ISSUE_LIN}"
            }
            throw new GitflowException(errorMsg)
        }
    }

}

