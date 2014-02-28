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

import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
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
    def keep
    def msgPrefix
    def msgSuffix
    def push = true

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
            throw new GitflowException("There is an existing hotfix branch. Finish that one first.")
        }

        // require_clean_working_tree
        init.requireCleanWorkingTree()

        init.requireBranchAbsent(hotfixBranch)

        init.requireTagAbsent(tagName)

        def origin = init.getOrigin()
        if(origin){
            // if the origin branch counterpart exists, fetch and assert that
            // the local branch isn't behind it (to avoid unnecessary rebasing)
            init.executeRemote("git fetch --all")

            if(init.gitBranchExists("${origin}/${master}")){
                init.requireBranchesEqual(master, "${origin}/${master}")
            }
        }

        // create branch
        init.executeLocal("git checkout -b ${hotfixBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push ${origin} ${hotfixBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${hotfixBranch}' to '${origin}'. Please ensure your username and password is in your ~/_netrc file"
                } else {
                    errorMsg = "Issue pushing feature branch '${hotfixBranch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
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
        log.info "- Bump the version number now!"
        log.info "- Start committing your hot fixes"
        log.info "- When done, run:"
        log.info ""
        log.info "     git flow hotfix finish '${hotfixBranchName}'"
        log.info ""

    }

    void finish(String hotfixBranchName) throws GitflowException, GitflowMergeConflictException {
        init.requireGitRepo()

        if(!hotfixBranchName) {
            throw new GitflowException("Missing argument <hotfixBranchName>")
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
        def tagName = versionPrefix + hotfixBranchName

        // sanity checks
        init.requireLocalBranch(hotfixBranch)
        init.requireCleanWorkingTree()
        init.requireTagAbsent(tagName)

        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(origin){
            // if the origin exists, fetch and assert that
            init.executeRemote("git fetch --all")

            if(init.gitBranchExists("${origin}/${hotfixBranch}")){
                init.requireBranchesEqual(hotfixBranch, "${origin}/${hotfixBranch}")
            }
            if(init.gitBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
            if(init.gitBranchExists("${origin}/${master}")){
                init.requireBranchesEqual(master, "${origin}/${master}")
            }
        }

        // try to merge into master
        // in case a previous attempt to finish this release branch has failed,
        // but the merge into master was successful, we skip it now
        if(!init.gitIsBranchMergedInto(hotfixBranch, master)){
            init.executeLocal("git checkout ${master}")

            def msg = "${msgPrefix}Merge branch '${hotfixBranch}' into ${master}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git","merge","-m '${msg}'","--no-ff","${hotfixBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${hotfixBranch}' into ${master}${msgSuffix}"
                init.executeLocal("git merge --squash ${hotfixBranch}")
                init.executeLocal(["git", "commit", "-m '${squashMsg}'"])

                init.executeLocal(["git", "merge", "-m '${msg}'", "${hotfixBranch}"])
            }
        }

        if(!init.gitTagExists(tagName)){
            def tagMsg = "Hotfix_version_${tagName}"
            if(sign){
                if(!signingkey){
                    throw new GitflowException("Missing argument <signingkey>")
                }
                init.executeLocal("git tag -u ${signingkey} -m \"${tagMsg}\" ${tagName} ${master}")
            } else{
                init.executeLocal("git tag -a -m \"${tagMsg}\" ${tagName} ${master}")
            }
        }

        // try to merge into develop
        // in case a previous attempt to finish this release branch has failed,
        // but the merge into develop was successful, we skip it now
        if(!init.gitIsBranchMergedInto(hotfixBranch, develop)){
            init.executeLocal("git checkout ${develop}")

            // TODO: Actually, accounting for 'git describe' pays, so we should
            // ideally git merge --no-ff $tagname here, instead!
            def msg = "${msgPrefix}Merge branch '${hotfixBranch}' into ${develop}${msgSuffix}"
            if(!squash){
                init.executeLocal(["git","merge","-m '${msg}'","--no-ff","${hotfixBranch}"])
            } else {
                def squashMsg = "${msgPrefix}Squashing branch '${hotfixBranch}' into ${develop}${msgSuffix}"
                init.executeLocal("git merge --squash ${hotfixBranch}")
                init.executeLocal(["git", "commit", "-m '${squashMsg}'"])

                init.executeLocal(["git", "merge", "-m '${msg}'", "${hotfixBranch}"])
            }
        }

        if (!keep) {
            def curr = init.gitCurrentBranch()
            if(hotfixBranch == curr){
                init.executeLocal("git checkout ${master}")
            }
            init.executeLocal("git branch -d ${hotfixBranch}")
        }

        // push it
        if(push && origin) {
            def pushing = [develop,master,tagName]
            for (branch in pushing) {
                log.debug "Pushing ${branch}"
                Integer exitCode = init.executeRemote("git push ${origin} ${branch}")
                if(exitCode){
                    def errorMsg
                    if (System.properties['os.name'].toLowerCase().contains("windows")) {
                        errorMsg = "Issue pushing feature branch '${branch}' to '${origin}'. Please ensure your username and password is in your ~/_netrc file"
                    } else {
                        errorMsg = "Issue pushing feature branch '${branch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                    }
                    throw new GitflowException(errorMsg)
                }
            }
        }

        if(origin){
            //Delete remote hotfix branch
            if (!keep) {
                init.executeRemote("git push ${origin} :${hotfixBranch}")
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
        if(keep){
            log.info "- Hotfix branch '${hotfixBranch}' is still available"
        }
        else {
            "- Hotfix branch '${hotfixBranch}' has been deleted"
        }
        if(origin){
            log.info "- '${develop}', '${master}' and tags have been pushed to '${origin}'"
        }
        if(push && origin){
            log.info "- '${develop}', '${master}' and ${tagName} tag have been pushed to '${origin}'"
        }
        log.info ""
    }
}
