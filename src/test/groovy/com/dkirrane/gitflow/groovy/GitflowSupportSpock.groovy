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

import groovy.util.logging.Slf4j
import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 *
 */
@Stepwise
@Slf4j
class GitflowSupportSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final SUPPORT_BRN_NAME = '1.x'

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        log.info "repoDir = $repoDir"
        def process = "git init".execute(null, repoDir)
        process.waitForOrKill(10000L)

        init = new GitflowInit(repoDir:repoDir)
        init.cmdDefault()

        //        if(System.properties['os.name'].toLowerCase().contains("windows")){
        //            def cmd = "explorer ${repoDir.getCanonicalPath()}"
        //        }

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //            def streeProcess = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}".execute(null, repoDir)
        //        }
    }

    def cleanupSpec() {
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
        branches.sort() == ["develop", "master", prefix + SUPPORT_BRN_NAME].sort()
    }

}

