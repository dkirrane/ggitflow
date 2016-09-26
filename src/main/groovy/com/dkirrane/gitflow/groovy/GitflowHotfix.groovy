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
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import com.dkirrane.gitflow.groovy.prompt.Prompter
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
class GitflowHotfix {

    @Delegate GitflowInit init

    def startCommit
    def squash
    def sign
    def signingkey
    def keepLocal
    def keepRemote
    def msgPrefix
    def msgSuffix
    def push

    void start(String hotfixBranchName) throws GitflowException {
        init.requireGitRepo()

        if(!hotfixBranchName) {
            throw new GitflowException("Missing argument <hotfixBranchName>")
        }

        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }

        def prefix = init.getHotfixBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(hotfixBranchName.contains(prefix)){
            hotfixBranchName = hotfixBranchName.minus(prefix)
        }
        def hotfixBranch = prefix + hotfixBranchName
        def tagName = versionPrefix + hotfixBranchName

        // require_base_is_on_master
        def master = init.getMasterBranch()
        if(startCommit) {
            if(!init.gitIsBranchMergedInto(startCommit,master)){
                throw new GitflowException("Given start commit '${startCommit}' is not a valid commit on '${master}'.")
            }
        } else {
            startCommit = master
        }

        // require_no_existing_hotfix_branches
        List<String> allBranches = init.gitAllBranches()
        if( allBranches.any({ it.contains(prefix) }) ){
            def found = allBranches.findAll { it.contains(prefix) }
            throw new GitflowException("There is an existing hotfix branch. Finish that one first: ${found}")
        }

        // require_clean_working_tree
        init.requireCleanWorkingTree()

        init.requireBranchAbsent(hotfixBranch)

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

            if(init.gitRemoteBranchExists("${origin}/${master}")){
                init.requireBranchesEqual(master, "${origin}/${master}")
            }
        }

        // create branch
        init.executeLocal("git checkout -b ${hotfixBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push -u ${origin} ${hotfixBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${hotfixBranch}' to '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue pushing feature branch '${hotfixBranch}' to '${origin}'. ${PUSH_ISSUE_LIN}"
                }
                throw new GitflowException(errorMsg)
            }
        }

        log.info ""
        log.info "Summary of actions:"
        log.info "- A new branch '${hotfixBranch}' was created, based on '${startCommit}'"
        log.info "- You are now on branch '${hotfixBranch}'"
        log.info ""
        log.info "Follow-up actions:"
        log.info "- Start committing your bug fixes"
        log.info "- When done, run:"
        log.info ""
        log.info "     mvn gitflow hotfix-finish command"
        log.info ""

    }

    void finish(String hotfixBranchName, String tagName) throws GitflowException, GitflowMergeConflictException {
        finishToMaster(hotfixBranchName, tagName)
        finishToDevelop(hotfixBranchName, tagName)
    }

    void finishToMaster(String hotfixBranchName, String tagName) throws GitflowException, GitflowMergeConflictException {
        init.requireGitRepo()

        if(!hotfixBranchName) {
            throw new GitflowException("Missing argument <hotfixBranchName>")
        }
        if(!tagName) {
            throw new GitflowException("Missing argument <tagName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }
        msgPrefix = msgPrefix ? msgPrefix + " " : ""
        msgSuffix = msgSuffix ? " " + msgSuffix : ""

        def prefix = init.getHotfixBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(hotfixBranchName.contains(prefix)){
            hotfixBranchName = hotfixBranchName.minus(prefix)
        }
        def hotfixBranch = prefix + hotfixBranchName

        // sanity checks
        init.requireLocalBranch(hotfixBranch)
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

            if(init.gitRemoteBranchExists("${origin}/${hotfixBranch}")){
                init.requireLocalBranchNotBehind(hotfixBranch, "${origin}/${hotfixBranch}") // local hotfix branch may have a commit containing the Maven version change that was not pushed to remote branch
            }
            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
            if(init.gitRemoteBranchExists("${origin}/${master}")){
                if(init.gitIsBranchMergedInto(hotfixBranch, master)){
                    init.requireLocalBranchNotBehind(master, "${origin}/${master}") // local hotfix branch may have already been merged to local master i.e. re-running finish after merge conflict
                } else {
                    init.requireBranchesEqual(master, "${origin}/${master}")
                }
            }
        }

        // If the branch is already merged there is no need to check the hotfix branch
	// This can happen when the merge in develop fails and we rerun the finish.
        if(!init.gitIsBranchMergedInto(hotfixBranch, master)){
            // Check if the hotfix branch:
	    // - has commits: No reason to finish a hotfix without commits
            // - Is ahead of the BASE: If it's not a good idea to merge
            // - Can be merged: If there's no common ancestor we can't merge the hotfix
            Integer result = init.gitCompareBranches(hotfixBranch, master);
            switch (result) {
            case 0:
                throw new GitflowException("You need some commits in the hotfix branch '${hotfixBranch}'. No reason to finish a hotfix without commits!!!");
            case 1:
                throw new GitflowException("The hotfix branch '${hotfixBranch}' is not ahead of branch '${master}'. It's not a good idea to merge!!!");
            case 4:
                throw new GitflowException("The hotfix branch '${hotfixBranch}' has no common ancestor with branch '${master}'. Cannot merge this hotfix branch!!!");
            default:
                log.debug "Compared Branches ${result}"
            }
        }

	// We ask for a tag, be sure it does not exist or
        // points to the latest hotfix commit
        if(init.gitTagExists(tagName)){
            Integer result = init.gitCompareBranches(hotfixBranch, master);
            if(0 != result){
                throw new GitflowException("Tag '${tagName}' already exists and does not point to hotfix branch '${hotfixBranch}'");
            }
        }

        // try to merge into master
        // in case a previous attempt to finish this hotfix branch has failed,
        // but the merge into master was successful, we skip it now
        if(!init.gitIsBranchMergedInto(hotfixBranch, master)){
            log.info "Merging hotfix branch ${hotfixBranch} into ${master}"

            // Before we merge be sure the tag does not exist
            init.requireTagAbsent(tagName)

            init.executeLocal("git checkout ${master}")

            def msg = "${msgPrefix}Merge branch '${hotfixBranch}' into ${master}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "--no-ff", "${hotfixBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${hotfixBranch}' into ${master}${msgSuffix}"
                init.executeLocal("git merge --squash ${hotfixBranch}")
                init.executeLocal(["git", "commit", "-m", "\"${squashMsg}\""])

                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "${hotfixBranch}"])
            }
        }

        // Try to tag the hotfix.
        // In case a previous attempt to finish this hotfix branch has failed,
        // but the tag was set successful, we skip it now
        if(!init.gitTagExists(tagName)){
            log.info "Tagging hotfix branch ${hotfixBranch} on ${master}"
            def tagMsg = "Hotfix version ${tagName}"
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

    void finishToDevelop(String hotfixBranchName, String tagName) throws GitflowException, GitflowMergeConflictException {
        init.requireGitRepo()

        if(!hotfixBranchName) {
            throw new GitflowException("Missing argument <hotfixBranchName>")
        }
        if(!tagName) {
            throw new GitflowException("Missing argument <tagName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }
        msgPrefix = msgPrefix ? msgPrefix + " " : ""
        msgSuffix = msgSuffix ? " " + msgSuffix : ""

        def prefix = init.getHotfixBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(hotfixBranchName.contains(prefix)){
            hotfixBranchName = hotfixBranchName.minus(prefix)
        }
        def hotfixBranch = prefix + hotfixBranchName

        // sanity checks
        init.requireLocalBranch(hotfixBranch)
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

            if(init.gitRemoteBranchExists("${origin}/${hotfixBranch}")){
                init.requireLocalBranchNotBehind(hotfixBranch, "${origin}/${hotfixBranch}") // local branch may have a commit containing the Maven version change that was not pushed to remote branch
            }
            if(init.gitRemoteBranchExists("${origin}/${develop}")){
                init.requireLocalBranchNotBehind(develop, "${origin}/${develop}") // local hotfix branch may have already been merged to local develop i.e. re-running finish after merge conflict
            }
        }

        // try to merge into develop
        // in case a previous attempt to finish this release branch has failed,
        // but the merge into develop was successful, we skip it now
        if(!init.gitIsBranchMergedInto(hotfixBranch, develop)){
            log.info "Merging hotfix branch ${hotfixBranch} on ${develop}"
            init.executeLocal("git checkout ${develop}")

            // TODO: Actually, accounting for 'git describe' pays, so we should
            // ideally git merge --no-ff $tagname here, instead!
            def msg = "${msgPrefix}Merge branch '${hotfixBranch}' into ${develop}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "--no-ff", "${hotfixBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${hotfixBranch}' into ${develop}${msgSuffix}"
                init.executeLocal("git merge --squash ${hotfixBranch}")
                init.executeLocal(["git", "commit", "-m", "\"${squashMsg}\""])

                init.executeLocal(["git", "merge", "-m", "\"${msg}\"", "${hotfixBranch}"])
            }
        } else {
            log.warn "Hotfix branch ${hotfixBranch} has already been merged into ${develop}"
            init.executeLocal("git checkout ${develop}")
        }

        if(origin && !keepRemote){
            //Delete remote hotfix branch
            if(init.gitRemoteBranchExists("${origin}/${hotfixBranch}")){
                init.executeRemote("git push ${origin} :${hotfixBranch}")
            }
        }

        if (!keepLocal) {
            if(init.gitIsBranchMergedInto(hotfixBranch, develop)){
                def curr = init.gitCurrentBranch()
                if(hotfixBranch == curr){
                    init.executeLocal("git checkout ${develop}")
                }
                init.executeLocal("git branch -D ${hotfixBranch}")
            }
        }

        log.info ""
        log.info "Summary of actions:"
        if(origin){
            log.info "- Latest objects have been fetched from '${origin}'"
        }
        log.info "- Hotfix branch has been merged into '${master}'"
        log.info "- The hotfix was tagged '${tagName}'"
        log.info "- Hotfix branch has been back-merged into '${develop}'"
        if(keepLocal) {
            log.info "- Local Hotfix branch '${hotfixBranch}' is still available"
        }
        else {
            log.info "- Local Hotfix branch '${hotfixBranch}' has been deleted"
        }
        if(origin){
            if(keepRemote) {
                log.info "- Remote Hotfix branch '${hotfixBranch}' is still available from '${origin}'"
            }
            else {
                log.info "- Remote Hotfix branch '${hotfixBranch}' has been deleted from '${origin}'"
            }
        }
        log.info ""
    }

    void publish(String hotfixBranch, String tagName) throws GitflowException {
        log.info ""
        log.warn "===> Verify merge to ${develop} & ${master} before pushing!"
        //Prompt user to push or not
        String answer = Prompter.instance.prompt("Do you want to push '${develop}', '${master}', tag '${tagName}' to ${origin} and delete the remote branch '${origin}/${hotfixBranch}' ? (y/N)");
        if(answer.matches(/^([yY][eE][sS]|[yY])$/)) {
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
                push(origin, branch)
            }

            if(keepRemote){
                if (answer.matches(/^([yY][eE][sS]|[yY])$/)) {
                    //Delete remote hotfix branch
                    if(init.gitRemoteBranchExists("${origin}/${hotfixBranch}")){
                        init.executeRemote("git push ${origin} :${hotfixBranch}")
                    }
                }
            }

        } else {
            log.info ""
            log.warn "===> Once happy with the merge you MUST manually push '${develop}', '${master}' and tag '${tagName}' to '${origin}':"
            log.warn ""
            log.warn "        git push ${origin} ${develop}"
            log.warn "        git push ${origin} ${master}"
            log.warn "        git push ${origin} ${tagName}"
            log.warn ""
            if(keepRemote) {
                log.warn ""
                log.warn "===> And manually delete the remote hotfix branch '${hotfixBranch}':"
                log.warn ""
                log.warn "        git push ${origin} --delete ${hotfixBranch}"
            }
            log.info ""
        }
    }

    void push(String origin, String branch) throws GitflowException {
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

