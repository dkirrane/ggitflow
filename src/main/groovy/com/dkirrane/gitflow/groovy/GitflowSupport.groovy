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
    def push

    void start(String supportBranchName) throws GitCommandException, GitflowException {
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

        if(init.gitBranchExists(supportBranch)){
            throw new GitflowException("ERROR: support branch ${supportBranch} already exists")
        }

        // create branch
        init.executeLocal("git checkout -b ${supportBranch} ${startCommit}")

        // push it
        if(push && origin) {
            Integer exitCode = init.executeRemote("git push -u ${origin} ${supportBranch}")
            if(exitCode){
                def errorMsg
                if (System.properties['os.name'].toLowerCase().contains("windows")) {
                    errorMsg = "Issue pushing feature branch '${supportBranch}' to '${origin}'. ${PUSH_ISSUE_WIN}"
                } else {
                    errorMsg = "Issue pushing feature branch '${supportBranch}' to '${origin}'. ${PUSH_ISSUE_LIN}"
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

