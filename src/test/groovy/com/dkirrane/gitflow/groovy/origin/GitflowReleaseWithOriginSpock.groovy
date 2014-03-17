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
import com.dkirrane.gitflow.groovy.GitflowRelease

import com.dkirrane.gitflow.groovy.origin.util.GitHubUtil
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class GitflowReleaseWithOriginSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    static final RELEASE_BRN_NAME = '1.0'

    static final Random rand = new Random()
    static final REPO_NAME = 'ReleaseTest' + rand.nextInt()

    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");

        GitHubUtil.createRepo(repoDir, REPO_NAME)
        log.info "repoDir = $repoDir"

        init = new GitflowInit(repoDir:repoDir)

        //        if(System.properties['os.name'].toLowerCase().contains("windows")){
        //            def cmd = "explorer ${repoDir.getCanonicalPath()}"
        //        }
    }

    def cleanupSpec() {
        GitHubUtil.deleteRepo(REPO_NAME)
        tempFolder.delete()
    }

    def "Gitflow release start"() {
        given: "GitflowRelease"
        def release = new GitflowRelease(init:init,push:true)

        when: "start is called"
        release.start(RELEASE_BRN_NAME)

        then: "the release branch should be created"
        List branches = init.gitAllBranches();
        def prefix = init.getReleaseBranchPrefix()
        branches.sort() == ["develop", "master", prefix + RELEASE_BRN_NAME, "origin/develop", "origin/master", "origin/${prefix}${RELEASE_BRN_NAME}"].sort()
    }

    def "Gitflow release finish"() {
        given: "GitflowRelease"
        def release = new GitflowRelease(init:init,push:true)

        new File(repoDir, "someCommit.txt").withWriter { out ->
            out.println "Commit " + RELEASE_BRN_NAME
        }
        init.executeLocal("git add -A .")
        init.executeLocal("git commit -m \"CommitSomething\"")

        when: "finish is called"
        def prefix = init.getReleaseBranchPrefix()
        def releaseBranch = prefix + RELEASE_BRN_NAME
        release.finish(releaseBranch)

        then: "the release branch should no longer exist"
        List branches = init.gitAllBranches();
        branches.sort() == ["develop", "master", "origin/develop", "origin/master"].sort()

        and: "the release branch should be merged into develop"
        def commitMessage = init.executeLocal("git log -1 --pretty=%B")
        assert commitMessage.contains("Merge branch '${releaseBranch}' into develop")
    }

}

