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
class GitflowRelease {

    @Delegate GitflowInit init

    def startCommit
    def squash
    def sign
    def signingkey
    def keep
    def msgPrefix
    def msgSuffix
    def push

    void start(String releaseBranchName) throws GitflowException {
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
            throw new GitflowException("There is an existing release branch. Finish that one first.")
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
                    errorMsg = "Issue fetching from '${origin}'. Please ensure your username and password is in your ~/_netrc file"
                } else {
                    errorMsg = "Issue fetching from '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                }
                throw new GitflowException(errorMsg)
            }

            if(init.gitBranchExists("${origin}/${develop}")){
                init.requireBranchesEqual(develop, "${origin}/${develop}")
            }
        }

        // create branch
        init.executeLocal("git checkout -b ${releaseBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push ${origin} ${releaseBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${releaseBranch}' to '${origin}'. Please ensure your username and password is in your %USERPROFILE%\\_netrc file"
                } else {
                    errorMsg = "Issue pushing feature branch '${releaseBranch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
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
        log.info "     mvn ggitflow:release-finish"
        log.info ""

    }

    void finish(String releaseBranchName) throws GitflowException, GitflowMergeConflictException {
        finishToMaster(releaseBranchName)
        finishToDevelop(releaseBranchName)
    }

    void finishToMaster(String releaseBranchName) throws GitflowException, GitflowMergeConflictException {
        init.requireGitRepo()

        if(!releaseBranchName) {
            throw new GitflowException("Missing argument <releaseBranchName>")
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
        def tagName = versionPrefix + releaseBranchName

        // sanity checks
        init.requireLocalBranch(releaseBranch)
        init.requireCleanWorkingTree()
        init.requireTagAbsent(tagName)

        def origin = init.getOrigin()
        def master = init.getMasterBranch()
        if(origin){
            // if the origin exists, fetch and assert that
            Integer exitCode = init.executeRemote("git fetch --all")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue fetching from '${origin}'. Please ensure your username and password is in your ~/_netrc file"
                } else {
                    errorMsg = "Issue fetching from '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                }
                throw new GitflowException(errorMsg)
            }
        }
        if(init.gitBranchExists("${origin}/${releaseBranch}")){
            init.requireBranchesEqual(releaseBranch, "${origin}/${releaseBranch}")
        }
        if(init.gitBranchExists("${origin}/${master}")){
            init.requireBranchesEqual(master, "${origin}/${master}")
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

        // push it
        if(origin) {
            log.info "Pushing ${tagName}"
            Integer exitCodeTag = init.executeRemote("git push ${origin} ${tagName}")
            if(exitCodeTag){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing '${tagName}' to '${origin}'. Please ensure your username and password is in your %USERPROFILE%\\_netrc file"
                } else {
                    errorMsg = "Issue pushing '${tagName}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                }
                throw new GitflowException(errorMsg)
            }            
            
            def pushing = [master]
            for (branch in pushing) {
                if(!init.gitBranchExists("${origin}/${branch}")){
                    log.debug "Remote branch ${branch} does not exists. Skipping push"
                    continue;
                }
                log.info "Pushing ${branch}"
                Integer exitCode = init.executeRemote("git push ${origin} ${branch}")
                if(exitCode){
                    def errorMsg
                    if (System.properties['os.name'].toLowerCase().contains("windows")) {
                        errorMsg = "Issue pushing '${branch}' to '${origin}'. Please ensure your username and password is in your %USERPROFILE%\\_netrc file"
                    } else {
                        errorMsg = "Issue pushing '${branch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                    }
                    throw new GitflowException(errorMsg)
                }
            }
        }

    }

    void finishToDevelop(String releaseBranchName) throws GitflowException, GitflowMergeConflictException {
        init.requireGitRepo()

        if(!releaseBranchName) {
            throw new GitflowException("Missing argument <releaseBranchName>")
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
        def tagName = versionPrefix + releaseBranchName

        // sanity checks
        init.requireLocalBranch(releaseBranch)
        init.requireCleanWorkingTree()

        def origin = init.getOrigin()
        def develop = init.getDevelopBranch()
        def master = init.getMasterBranch()
        if(init.gitBranchExists("${origin}/${releaseBranch}")){
            init.requireBranchesEqual(releaseBranch, "${origin}/${releaseBranch}")
        }
        if(init.gitBranchExists("${origin}/${develop}")){
            init.requireBranchesEqual(develop, "${origin}/${develop}")
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
        }

        if (!keep) {
            def curr = init.gitCurrentBranch()
            if(releaseBranch == curr){
                init.executeLocal("git checkout ${master}")
            }
            init.executeLocal("git branch -d ${releaseBranch}")
        }

        // push it
        if(origin) {
            def pushing = [develop]
            for (branch in pushing) {
                if(!init.gitBranchExists("${origin}/${branch}")){
                    log.debug "Remote branch ${branch} does not exists. Skipping push"
                    continue;
                }
                log.info "Pushing ${branch}"
                Integer exitCode = init.executeRemote("git push ${origin} ${branch}")
                if(exitCode){
                    def errorMsg
                    if (System.properties['os.name'].toLowerCase().contains("windows")) {
                        errorMsg = "Issue pushing branch '${branch}' to '${origin}'. Please ensure your username and password is in your %USERPROFILE%\\_netrc file"
                    } else {
                        errorMsg = "Issue pushing branch '${branch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                    }
                    throw new GitflowException(errorMsg)
                }
            }
        }

        if(push && origin){
            //Delete remote release branch
            if (!keep) {
                init.executeRemote("git push ${origin} :${releaseBranch}")
            }
        }

        init.executeLocal("git checkout ${develop}")

        log.info ""
        log.info "Summary of actions:"
        if(origin){
            log.info "- Latest objects have been fetched from '${origin}'"
        }
        log.info "- Release branch has been merged into '${master}'"
        log.info "- The release was tagged '${tagName}'"
        log.info "- Release branch has been back-merged into '${develop}'"
        if(keep){
            log.info "- Release branch '${releaseBranch}' is still available"
        }
        else {
            log.info "- Release branch '${releaseBranch}' has been deleted"
        }
        if(push && origin){
            log.info "- '${develop}', '${master}' and ${tagName} tag have been pushed to '${origin}'"
        }
        log.info ""
    }
}

