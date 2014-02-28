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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.GitflowInit

import com.dkirrane.gitflow.groovy.origin.util.GitHubUtil
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class GitflowInitExistingOriginMasterAndDevelop extends Specification  {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def setupRepoDir
    @Shared def repoDir

    static final Random rand = new Random()
    static final REPO_NAME = 'InitTest' + rand.nextInt()

    def setupSpec() {
        tempFolder.create();
        setupRepoDir = tempFolder.newFolder("setupRepo");
        repoDir = tempFolder.newFolder("repoDir");

        GitHubUtil.createRepo(setupRepoDir, REPO_NAME)
        log.info "setupRepoDir = $setupRepoDir"

        def init = new GitflowInit(repoDir:setupRepoDir)

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //            def streeProcess = "/usr/local/bin/stree ${setupRepoDir.getCanonicalPath()}".execute(null, setupRepoDir)
        //        }

        //create remote master
        init.executeLocal(["git", "commit", "--allow-empty", "-q", "-m", "Initial Commit"])
        init.executeRemote("git push origin master")

        //create remote develop
        init.executeLocal("git checkout -b develop master")
        init.executeLocal(["git", "commit", "--allow-empty", "-q", "-m", "Develop Commit"])
        init.executeRemote("git push origin develop")

        def repoURL = init.executeLocal("git config --get remote.origin.url")

        def process = "git clone ${repoURL} ${repoDir.getCanonicalPath()}".execute()
        process.consumeProcessOutput(System.out, System.err)
        process.waitForOrKill(20000L)

        //        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
        //            def streeProcess = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}".execute(null, repoDir)
        //        }
    }

    def cleanupSpec() {
        GitHubUtil.deleteRepo(REPO_NAME)
        tempFolder.delete()
    }

    def "Create Git repo"() {
        expect:
        assert (new File(repoDir, ".git").exists())
    }

    def "Gitflow init"() {
        given: "GitflowInit"
        def gitflowInit = new GitflowInit(repoDir:repoDir)

        when: "init is called"
        gitflowInit.cmdDefault()

        then: "the master branch exists"
        assert gitflowInit.gitLocalBranchExists("master")

        and: "the remote master branch exists"
        assert gitflowInit.gitRemoteBranchExists("origin/master")

        and: "the develop branch exists"
        assert gitflowInit.gitLocalBranchExists("develop")

        and: "the remote develop branch exists"
        assert gitflowInit.gitRemoteBranchExists("origin/develop")

        and: "the master branch should be called master"
        def masterBranch1 = gitflowInit.getMasterBranch()
        def masterBranch = "git config --get gitflow.branch.master".execute(null, repoDir).text.trim()
        masterBranch1 == masterBranch
        masterBranch == "master"

        and: "the develop branch should be called develop"
        def developBranch = "git config --get gitflow.branch.develop".execute(null, repoDir).text.trim()
        developBranch == "develop"

        and: "the feature prefix should be called feature/"
        def featurePrefix = "git config --get gitflow.prefix.feature".execute(null, repoDir).text.trim()
        featurePrefix == "feature/"

        and: "the release prefix should be called release/"
        def releasePrefix = "git config --get gitflow.prefix.release".execute(null, repoDir).text.trim()
        releasePrefix == "release/"

        and: "the hotfix prefix should be called hotfix/"
        def hotfixPrefix = "git config --get gitflow.prefix.hotfix".execute(null, repoDir).text.trim()
        hotfixPrefix == "hotfix/"

        and: "the support prefix should be called support/"
        def supportPrefix = "git config --get gitflow.prefix.support".execute(null, repoDir).text.trim()
        supportPrefix == "support/"

        and: "the version tag prefix should be called an empty string"
        def versionTagPrefix = "git config --get gitflow.prefix.versiontag".execute(null, repoDir).text.trim()
        versionTagPrefix == ""

        and: "the master and develop branches should exist"
        List branches = gitflowInit.gitAllBranches();
        branches.minus(".*HEAD.*").sort() == ["develop", "master", "origin/HEAD -> origin/master", "origin/develop", "origin/master"].sort()
    }
}

