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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 *
 */
@Slf4j
class GitflowEndToEnd extends Specification {

    @Rule TemporaryFolder tempFolder = new TemporaryFolder()
    def repoDir
    def init

    def setup() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        log.debug "repoDir = $repoDir"
        def process = "git init".execute(null, repoDir)
        process.waitForOrKill(10000L)

        init = new GitflowInit(repoDir:repoDir)

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //            def streeProcess = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}".execute(null, repoDir)
        //        }
    }

    def cleanup() {
        tempFolder.delete()
    }

    def "Gitflow end-to-end flow"() {
        given: "Gitflow"

        def feature = new GitflowFeature(init:init)
        def FEATURE_BRN_NAME = 'JIRA-123'

        def release = new GitflowRelease(init:init)
        def RELEASE_BRN_NAME = '1.0'

        def hotfix = new GitflowHotfix(init:init)
        def HOTFIX_BRN_NAME = '1.0.1'

        def support = new GitflowSupport(init:init,startCommit:"master")
        def SUPPORT_BRN_NAME = '1.0.x'

        when: "run end-to-end"

        feature.start(FEATURE_BRN_NAME)
        init.executeLocal("git commit --allow-empty -m \"${FEATURE_BRN_NAME}-Commit1\"", false)
        init.executeLocal("git commit --allow-empty -m \"${FEATURE_BRN_NAME}-Commit2\"", false)
        feature.finish(FEATURE_BRN_NAME)
        log.debug "feature complete"

        release.start(RELEASE_BRN_NAME)
        init.executeLocal("git commit --allow-empty -m \"${RELEASE_BRN_NAME}-Commit\"", false)
        release.finish(RELEASE_BRN_NAME)
        log.debug "release complete"

        hotfix.start(HOTFIX_BRN_NAME)
        init.executeLocal("git commit --allow-empty -m \"${HOTFIX_BRN_NAME}-Commit\"", false)
        hotfix.finish(HOTFIX_BRN_NAME)
        log.debug "hotfix complete"

        support.start(SUPPORT_BRN_NAME)
        init.executeLocal("git commit --allow-empty -m \"${SUPPORT_BRN_NAME}-Commit\"", false)
        log.debug "support started"

        then: "the branches should be created and finished"
        log.debug ""
        //        List branches = init.gitAllBranches();
        //        def prefix = init.getFeatureBranchPrefix()
        //        branches.sort() == ["develop", prefix + FEATURE_BRN_NAME, "master"].sort()
    }

    def "Gitflow feature squash"() {
        given: "Gitflow"

        def feature = new GitflowFeature(init:init)
        def FEATURE_BRN_NAME = 'JIRA-123'

        def release = new GitflowRelease(init:init)
        def RELEASE_BRN_NAME = '1.0'

        def hotfix = new GitflowHotfix(init:init)
        def HOTFIX_BRN_NAME = '1.0.1'

        def support = new GitflowSupport(init:init,startCommit:"master")
        def SUPPORT_BRN_NAME = '1.0.x'

        when: "run end-to-end"

        feature.start(FEATURE_BRN_NAME)
        init.executeLocal("git commit --allow-empty -m \"${FEATURE_BRN_NAME}-Commit1\"", false)
        init.executeLocal("git commit --allow-empty -m \"${FEATURE_BRN_NAME}-Commit2\"", false)
        feature.finish(FEATURE_BRN_NAME)
        log.debug "feature complete"

        then: "the branches should be created and finished"
        log.debug ""
    }

}

