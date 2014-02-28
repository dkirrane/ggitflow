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
package com.dkirrane.gitflow.groovy.conflict

import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import com.dkirrane.gitflow.groovy.GitflowInit
import com.dkirrane.gitflow.groovy.GitflowFeature
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class GitflowFeatureMergeConflictSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final FEATURE_BRN_NAME = 'JIRA-123'
    static final FILE_NAME = "test-merge-conflict.txt"

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        log.info "repoDir = $repoDir"
        def process = "git init".execute(null, repoDir)
        process.waitForOrKill(10000L)

        init = new GitflowInit(repoDir:repoDir)
        init.cmdDefault()

        File testFile = new File(repoDir, FILE_NAME)
        assert testFile.createNewFile()
        testFile.write('hello world')
        init.executeLocal(["git", "add", "-A"])
        init.executeLocal(["git", "commit", "-m", "BASE Commit"])

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //            def streeProcess = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}".execute(null, repoDir)
        //        }
    }

    def cleanupSpec() {
        tempFolder.delete()
    }

    def processFileInplace(file, Closure processText) {
        def text = file.text
        file.write(processText(text))
    }

    def "Gitflow feature start"() {
        given: "GitflowFeature"
        def feature = new GitflowFeature(init:init)

        when: "start is called"
        feature.start(FEATURE_BRN_NAME)

        File testFile = new File(repoDir, FILE_NAME)
        assert testFile.exists()
        processFileInplace(testFile) { text ->
            text.replaceAll(/hello world/, 'Hello World')
        }
        init.executeLocal(["git", "commit", "-a", "-m", "REMOTE Commit"])

        then: "the feature branch should be created"
        List branches = init.gitAllBranches();
        def prefix = init.getFeatureBranchPrefix()
        branches.sort() == ["develop", prefix + FEATURE_BRN_NAME, "master"].sort()
    }

    def "Gitflow feature finish"() {
        given: "GitflowFeature"
        def feature = new GitflowFeature(init:init)

        def prefix = init.getFeatureBranchPrefix()
        def featureBranch = prefix + FEATURE_BRN_NAME

        init.executeLocal(["git", "checkout", "develop"])
        File testFile = new File(repoDir, FILE_NAME)
        assert testFile.exists()
        processFileInplace(testFile) { text ->
            text.replaceAll(/hello world/, 'Hello World!!!')
        }
        init.executeLocal(["git", "commit", "-a", "-m", "LOCAL Commit"])
        init.executeLocal(["git", "checkout", "${featureBranch}"])

        when: "finish is called"
        feature.finish(featureBranch)

        then: "the feature branch should no longer exist"
        thrown(GitflowMergeConflictException.class)
    }

    def "Fix Merge Conflict and Gitflow feature finish again"() {
        given: "Merge Conflict Resolution and GitflowFeature"

        init.executeLocal(["git", "checkout", "--theirs", "--", "${FILE_NAME}"])
        def mergeResolvedMsg = "Resolving Merge Conflict in file ${FILE_NAME}"
        init.executeLocal(["git", "commit", "-a", "-m", "${mergeResolvedMsg}"])

        def feature = new GitflowFeature(init:init)

        def prefix = init.getFeatureBranchPrefix()
        def featureBranch = prefix + FEATURE_BRN_NAME

        when: "finish is called"
        feature.finish(featureBranch)

        then: "the feature branch should no longer exist"
        List branches = init.gitAllBranches();
        branches.sort() == ["develop", "master"].sort()

        and: "the feature branch should be merged into develop"
        def commitMessage = init.executeLocal("git log -1 --pretty=%B")
        assert commitMessage.contains(mergeResolvedMsg)
    }

}

