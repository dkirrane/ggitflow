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
class GitflowFeatureSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final FEATURE_BRN_NAME = 'JIRA-123'

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        log.info "repoDir = $repoDir"
        def process = "git init".execute(null, repoDir)
        process.waitForOrKill(10000L)

        init = new GitflowInit(repoDir:repoDir)

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //                    "open ${repoDir.getPath()}".execute()
        //            def cmd = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}"
        //            log.info "Running command ${cmd}"
        //            def streeProcess = cmd.execute(null, repoDir)
        //            streeProcess.consumeProcessOutput(System.out, System.err)
        //            log.info 'requested consume output' //hoping this will come out first
        //            streeProcess.waitForOrKill(5000L)
        //        }
    }

    def cleanupSpec() {
        tempFolder.delete()
    }

    def "Gitflow feature start"() {
        given: "GitflowFeature"
        def feature = new GitflowFeature(init:init)

        when: "start is called"
        feature.start(FEATURE_BRN_NAME)

        then: "the feature branch should be created"
        List branches = init.gitAllBranches();
        def prefix = init.getFeatureBranchPrefix()
        branches.sort() == ["develop", prefix + FEATURE_BRN_NAME, "master"].sort()
    }

    def "Gitflow feature finish"() {
        given: "GitflowFeature"
        def feature = new GitflowFeature(init:init)

        new File(repoDir, "Commit1.txt").withWriter { out ->
            out.println "Commit1 on " + FEATURE_BRN_NAME
        }
        init.executeLocal("git add -A .")
        init.executeLocal("git commit -m \"Commit1\"")

        new File(repoDir, "Commit2.txt").withWriter { out ->
            out.println "Commit2 on " + FEATURE_BRN_NAME
        }
        init.executeLocal("git add -A .")
        init.executeLocal("git commit -m \"Commit2\"")

        when: "finish is called"
        def prefix = init.getFeatureBranchPrefix()
        def featureBranch = prefix + FEATURE_BRN_NAME
        feature.finish(featureBranch)

        then: "the feature branch should no longer exist"
        List branches = init.gitAllBranches();
        branches.sort() == ["develop", "master"].sort()

        and: "the feature branch should be merged into develop"
        def commitMessage = init.executeLocal("git log -1 --pretty=%B")
        assert commitMessage.contains("Merge branch '${featureBranch}' into develop")
    }

}

