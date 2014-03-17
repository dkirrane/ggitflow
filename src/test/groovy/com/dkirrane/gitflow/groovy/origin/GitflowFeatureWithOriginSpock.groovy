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
package com.dkirrane.gitflow.groovy.origin

import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import com.dkirrane.gitflow.groovy.GitflowInit
import com.dkirrane.gitflow.groovy.GitflowFeature

import com.dkirrane.gitflow.groovy.origin.util.GitHubUtil
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class GitflowFeatureWithOriginSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final FEATURE_BRN_NAME = 'JIRA-123'

    static final Random rand = new Random()
    static final REPO_NAME = 'FeatureTest' + rand.nextInt()

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        GitHubUtil.createRepo(repoDir, REPO_NAME)
        log.info "repoDir = $repoDir"

        init = new GitflowInit(repoDir:repoDir)

        //        if(System.properties['os.name'].toLowerCase().contains('windows')){
        //            def cmd = "explorer \"${repoDir.getCanonicalPath()}\""
        //        }
    }

    def cleanupSpec() {
        GitHubUtil.deleteRepo(REPO_NAME)
        tempFolder.delete()
    }

    def "Gitflow feature start"() {
        given: "GitflowFeature"

        def feature = new GitflowFeature(init:init,push:true)

        when: "start is called"
        feature.start(FEATURE_BRN_NAME)

        then: "the feature branch should be created"
        List branches = init.gitAllBranches();
        def prefix = init.getFeatureBranchPrefix()
        // develop, feature/JIRA-123, master, origin/develop, origin/master
        branches.sort() == ["develop", prefix + FEATURE_BRN_NAME, "master", "origin/develop", "origin/${prefix}${FEATURE_BRN_NAME}", "origin/master"].sort()
    }

    def "Gitflow feature finish"() {
        given: "GitflowFeature"
        def feature = new GitflowFeature(init:init,push:true)

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
        branches.sort() == ["develop", "master", "origin/develop", "origin/master"].sort()

        and: "the feature branch should be merged into develop"
        def commitMessage = init.executeLocal("git log -1 --pretty=%B")
        assert commitMessage.contains("Merge branch '${featureBranch}' into develop")
    }

}

