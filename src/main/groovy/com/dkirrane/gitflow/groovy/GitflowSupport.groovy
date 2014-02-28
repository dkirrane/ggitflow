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
import groovy.util.logging.Slf4j

/**
 *
 */
@Slf4j
class GitflowSupport {

    @Delegate GitflowInit init

    def startCommit
    def msgPrefix
    def msgSuffix    

    void start(String supportBranchName) throws GitflowException {
        init.requireGitRepo()

        if(!supportBranchName) {
            throw new GitflowException("Missing argument <supportBranchName>")
        }
        if(!init.gitflowIsInitialized()){
            throw new GitflowException("Gitflow is not initialized.")
        }

        def prefix = init.getSupportBranchPrefix()
        def versionPrefix = init.getVersionTagPrefix()

        if(supportBranchName.contains(prefix)){
            supportBranchName = supportBranchName.minus(prefix)
        }
        def supportBranch = prefix + supportBranchName
        def tagName = versionPrefix + supportBranchName

        // require_base_is_on_master
        def master = init.getMasterBranch()
        if(startCommit) {
            def containsSC = init.gitIsBranchMergedInto(startCommit,master)
            if(!containsSC){
                throw new GitflowException("Given start commit '${startCommit}' is not a valid commit on '${master}'.")
            }
        } else {
            startCommit = master
        }

        // require_clean_working_tree
        init.requireCleanWorkingTree()

        init.requireBranchAbsent(supportBranch)

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

        if(init.gitBranchExists(supportBranch)){
            throw new GitflowException("ERROR: support branch ${supportBranch} already exists")
        }

        // create branch
        init.executeLocal("git checkout -b ${supportBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push ${origin} ${supportBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${supportBranch}' to '${origin}'. Please ensure your username and password is in your ~/_netrc file"
                } else {
                    errorMsg = "Issue pushing feature branch '${supportBranch}' to '${origin}'. Please ensure your username and password is in your ~/.netrc file"
                }
                throw new GitflowException(errorMsg)
            }
        }

        log.info ""
        log.info "Summary of actions:"
        log.info "- A new branch '${supportBranch}' was created, based on '${startCommit}'"
        log.info "- You are now on branch '${supportBranch}'"
        log.info ""
    }
}

