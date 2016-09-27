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
import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.ex.GitCommandException
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import com.dkirrane.gitflow.groovy.prompt.Prompter
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
class GitflowRelease {

    @Delegate GitflowInit init

    def startCommit
    def squash
    def sign
    def signingkey
    def msgPrefix
    def msgSuffix
    def push

    void start(String releaseBranchName) throws GitCommandException, GitflowException {
        init.requireGitRepo()

        if(!releaseBranchName) {
            throw new GitflowException("Missing argument <releaseBranchName>")
        }

        if(!init.gitflowIsInitialized()){
            init.cmdDefault()
        }

        String prefix = init.getReleaseBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(releaseBranchName.contains(prefix)){
            releaseBranchName = releaseBranchName.minus(prefix)
        }
        def releaseBranch = prefix + releaseBranchName
        def tagName = versionPrefix + releaseBranchName

        // require_base_is_on_develop
        def develop = init.getDevelopBranch()
        if(startCommit) {
            if(!init.gitIsBranchMergedInto(startCommit,develop)){
                throw new GitflowException("Given start commit '${startCommit}' is not a valid commit on '${develop}'.")
            }
        } else {
            startCommit = develop
        }

        // require_no_existing_release_branches
        List<String> allBranches = init.gitAllBranches()
        if( allBranches.any({ it.contains(prefix) }) ){
            def found = allBranches.findAll { it.contains(prefix) }
            throw new GitflowException("There is an existing release branch. Finish that one first: ${found}")
        }

        // require_clean_working_tree
        init.requireCleanWorkingTree()

        init.requireBranchAbsent(releaseBranch)

        init.requireTagAbsent(tagName)

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
        init.executeLocal("git checkout -b ${releaseBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push -u ${origin} ${releaseBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing release branch '${releaseBranch}' to '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue pushing release branch '${releaseBranch}' to '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }
        }

        log.info ""
        log.info "Summary of actions:"
        log.info "- A new branch '${releaseBranch}' was created, based on '${startCommit}'"
        log.info "- You are now on branch '${releaseBranch}'"
        log.info ""
        log.info "Follow-up actions:"
        log.info "- Start committing last-minute fixes in preparing your release"
        log.info "- When done, run:"
        log.info ""
        log.info "     mvn gitflow release-finish command"
        log.info ""

    }

    void finish(String releaseBranchName, String tagName) throws GitCommandException, GitflowMergeConflictException, GitflowException {
        finishToMaster(releaseBranchName, tagName)
        finishToDevelop(releaseBranchName, tagName)
    }

    void finishToMaster(String releaseBranchName, String tagName) throws GitCommandException, GitflowMergeConflictException, GitflowException {
        init.requireGitRepo()

        if(!releaseBranchName) {
            throw new GitflowException("Missing argument <releaseBranchName>")
        }
        if(!tagName) {
            throw new GitflowException("Missing argument <tagName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }
        msgPrefix = msgPrefix ? msgPrefix + " " : ""
        msgSuffix = msgSuffix ? " " + msgSuffix : ""

        def prefix = init.getReleaseBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(releaseBranchName.contains(prefix)){
            releaseBranchName = releaseBranchName.minus(prefix)
        }
        def releaseBranch = prefix + releaseBranchName

        // sanity checks
        init.requireLocalBranch(releaseBranch)
        init.requireCleanWorkingTree()

        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(origin){
            // if the origin exists, fetch and assert that branches are at the same commit
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

            if(init.gitRemoteBranchExists("${origin}/${releaseBranch}")){
                init.requireLocalBranchNotBehind(releaseBranch, "${origin}/${releaseBranch}") // local release branch may have a commit containing the Maven version change that was not pushed to remote branch
            }
            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
            if(init.gitRemoteBranchExists("${origin}/${master}")){
                if(init.gitIsBranchMergedInto(releaseBranch, master)){
                    init.requireLocalBranchNotBehind(master, "${origin}/${master}") // local release branch may have already been merged to local master i.e. re-running finish after merge conflict
                } else {
                    init.requireBranchesEqual(master, "${origin}/${master}")
                }
            }
        }

	// We ask for a tag, be sure it does not exist or
        // points to the latest release commit
        if(init.gitTagExists(tagName)){
            Integer result = init.gitCompareBranches(releaseBranch, master);
            if(0 != result){
                throw new GitflowException("Tag '${tagName}' already exists and does not point to release branch '${releaseBranch}'");
            }
        }

        // try to merge into master
        // in case a previous attempt to finish this release branch has failed,
        // but the merge into master was successful, we skip it now
        if(!init.gitIsBranchMergedInto(releaseBranch, master)){
            log.info "Merging release branch ${releaseBranch} into ${master}"

            init.executeLocal("git checkout ${master}")

            def msg = "${msgPrefix}Merge branch '${releaseBranch}' into ${master}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git","merge","-m '${msg}'","--no-ff","${releaseBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${releaseBranch}' into ${master}${msgSuffix}"
                init.executeLocal("git merge --squash ${releaseBranch}")
                init.executeLocal(["git", "commit", "-m", "\"${squashMsg}\""])

                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "${releaseBranch}"])
            }
        }

        // Try to tag the release.
        // In case a previous attempt to finish this release branch has failed,
        // but the tag was set successful, we skip it now
        if(!init.gitTagExists(tagName)){
            log.info "Tagging release branch ${releaseBranch} on ${master}"
            def tagMsg = "Release version ${tagName}"
            if(sign){
                if(!signingkey){
                    throw new GitflowException("Missing argument <signingkey>")
                }
                init.executeLocal(["git", "tag", "-u", "${signingkey}", "-m", "\"${tagMsg}\"", "${tagName}", "${master}"])
            } else{
                init.executeLocal(["git", "tag", "-a", "-m", "\"${tagMsg}\"", "${tagName}", "${master}"])
            }
        }

    }

    void finishToDevelop(String releaseBranchName, String tagName) throws GitCommandException, GitflowMergeConflictException, GitflowException {
        init.requireGitRepo()

        if(!releaseBranchName) {
            throw new GitflowException("Missing argument <releaseBranchName>")
        }
        if(!tagName) {
            throw new GitflowException("Missing argument <tagName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }
        msgPrefix = msgPrefix ? msgPrefix + " " : ""
        msgSuffix = msgSuffix ? " " + msgSuffix : ""

        def prefix = init.getReleaseBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(releaseBranchName.contains(prefix)){
            releaseBranchName = releaseBranchName.minus(prefix)
        }
        def releaseBranch = prefix + releaseBranchName

        // sanity checks
        init.requireLocalBranch(releaseBranch)
        init.requireCleanWorkingTree()

        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(origin){
            // if the origin exists, fetch and assert that branches are at the same commit
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

            if(init.gitRemoteBranchExists("${origin}/${releaseBranch}")){
                init.requireLocalBranchNotBehind(releaseBranch, "${origin}/${releaseBranch}") // local branch may have a commit containing the Maven version change that was not pushed to remote branch
            }
            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireLocalBranchNotBehind(develop, "${origin}/${develop}") // local release branch may have already been merged to local develop i.e. re-running finish after merge conflict
            }
        }

        // try to merge into develop
        // in case a previous attempt to finish this release branch has failed,
        // but the merge into develop was successful, we skip it now
        if(!init.gitIsBranchMergedInto(releaseBranch, develop)){
            log.info "Merging release branch ${releaseBranch} on ${develop}"
            init.executeLocal("git checkout ${develop}")

            // TODO: Actually, accounting for 'git describe' pays, so we should
            // ideally git merge --no-ff $tagname here, instead!
            def msg = "${msgPrefix}Merge branch '${releaseBranch}' into ${develop}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "--no-ff", "${releaseBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${releaseBranch}' into ${develop}${msgSuffix}"
                init.executeLocal("git merge --squash ${releaseBranch}")
                init.executeLocal(["git", "commit", "-m", "\"${squashMsg}\""])

                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "${releaseBranch}"])
            }
        } else {
            log.warn "Release branch ${releaseBranch} has already been merged into ${develop}"
            init.executeLocal("git checkout ${develop}")
        }

        log.info ""
        log.info "Summary of actions:"
        if(origin){
            log.info "- Latest objects have been fetched from '${origin}'"
        }
        log.info "- Release branch has been merged into '${master}'"
        log.info "- The release was tagged '${tagName}'"
        log.info "- Release branch has been back-merged into '${develop}'"
        log.info ""
    }

    void publish(String releaseBranch, String tagName, boolean pushIt) throws GitCommandException, GitflowException {
        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(pushIt && origin){
            log.info "Pushing tag ${tagName}"
            Integer exitCodeTag = init.executeRemote("git push ${origin} ${tagName}")
            if(exitCodeTag){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing '${tagName}' to '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue pushing '${tagName}' to '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }

            def pushing = [master,develop]
            for (branch in pushing) {
                pushBranch(origin, branch)
            }

            log.info "Deleting ${releaseBranch}"
            init.executeLocal("git branch -D ${releaseBranch}")

            // Delete remote release branch
            if(init.gitRemoteBranchExists("${origin}/${releaseBranch}")){
                log.info "Deleting ${origin}/${releaseBranch}"
                init.executeRemote("git push --delete ${origin} ${releaseBranch}")
            }

        } else {
            log.warn "===> Once happy with the merge you MUST manually push:"
            log.warn ""
            log.warn "        git push ${origin} ${develop}"
            log.warn "        git push ${origin} ${master}"
            log.warn "        git push ${origin} ${tagName}"
            log.warn ""
            log.warn ""
            log.warn "===> And manually delete the release branch:"
            log.warn ""
            log.warn "        git branch --delete ${releaseBranch}"
            if(init.gitRemoteBranchExists("${origin}/${releaseBranch}")){
                log.warn "        git push --delete ${origin} ${releaseBranch}"
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