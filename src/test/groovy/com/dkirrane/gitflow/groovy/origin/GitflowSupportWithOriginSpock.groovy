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

import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import com.dkirrane.gitflow.groovy.GitflowInit
import com.dkirrane.gitflow.groovy.GitflowSupport

import com.dkirrane.gitflow.groovy.origin.util.GitHubUtil
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class GitflowSupportWithOriginSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final SUPPORT_BRN_NAME = '1.x'

    static final Random rand = new Random()
    static final REPO_NAME = 'SupportTest' + rand.nextInt()

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        GitHubUtil.createRepo(repoDir, REPO_NAME)
        log.info "repoDir = $repoDir"

        init = new GitflowInit(repoDir:repoDir)
        init.cmdDefault()

        //        if(System.properties['os.name'].toLowerCase().contains("windows")){
        //            def cmd = "explorer ${repoDir.getCanonicalPath()}"
        //        }
    }

    def cleanupSpec() {
        GitHubUtil.deleteRepo(REPO_NAME)
        tempFolder.delete()
    }

    def "Gitflow support start"() {
        given: "GitflowSupport"
        def support = new GitflowSupport(init:init,startCommit:"master")

        when: "start is called"
        support.start(SUPPORT_BRN_NAME)

        then: "the support branch should be created"
        List branches = init.gitAllBranches();
        def prefix = init.getSupportBranchPrefix()
        branches.sort() == ["develop", "master", "origin/develop", "origin/master", "origin/${prefix}${SUPPORT_BRN_NAME}", "${prefix}${SUPPORT_BRN_NAME}"].sort()
    }

}

