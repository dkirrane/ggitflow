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
import com.dkirrane.gitflow.groovy.conflicts.FixPomMergeConflicts
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import groovy.util.logging.Slf4j

/**
 *
 */
@Stepwise
@Slf4j
class FixPomMergeConflictsSpock extends Specification {
    @Shared @ClassRule TemporaryFolder tempFolder = new TemporaryFolder()
    @Shared def repoDir
    @Shared def init
    @Shared def pom
    static final PROJECT_NAME = 'MergeTest'
    static final FILE_NAME = "test-merge-conflict.txt"

    /**
     * Sets up a merge conflict
     */
    def setupSpec() {
        tempFolder.create();
        repoDir = tempFolder.newFolder("repo");
        repoDir.mkdirs()
        log.info "repoDir = $repoDir"

        init = new GitflowInit(repoDir:repoDir)
        //        init.executeLocal("open ${repoDir}")

        def process = "git init".execute(null, repoDir)
        process.waitFor()

        // master
        println System.env
        def mvn = System.env.'M2_HOME' ?: "/Users/desmondkirrane/Development/apache-maven-3.0.5"
        println mvn
        assert mvn
        def exe = System.properties['os.name'].toLowerCase().contains('windows') ? "mvn.bat" : "mvn"
        def mvnExe = mvn + File.separator + "bin" + File.separator + exe
        println mvnExe
        assert mvnExe
        String createPom = "${mvnExe} archetype:generate -B -DarchetypeGroupId=org.codehaus.mojo.archetypes -DarchetypeArtifactId=pom-root -DarchetypeVersion=1.1 -DgroupId=com.mycompany -DartifactId=${PROJECT_NAME}"
        init.executeLocal(createPom)
        init.executeLocal(["git", "add", "-A"])
        init.executeLocal(["git", "commit", "-m", "Initial POM Project"])
        def project = new File(repoDir, PROJECT_NAME)
        assert project.exists()

        // branch
        init.executeLocal("git checkout -b myBranch")

        pom = new File(project, "pom.xml")
        processFileInplace(pom) { text ->
            text = text.replaceAll("1.0-SNAPSHOT", "1.1-SNAPSHOT")
        }
        init.executeLocal(["git", "commit", "-a", "-m", "myBranch version change"])
        //        processFileInplace(pom) { text ->
        //            text = text.replaceAll("${PROJECT_NAME}", "ProjMyBranch")
        //        }
        //        init.executeLocal(["git", "commit", "-a", "-m", "myBranch name change"])

        File testFile = new File(repoDir, "data.txt")
        assert testFile.createNewFile()
        testFile.write('hello world')
        init.executeLocal(["git", "add", "-A"])
        init.executeLocal(["git", "commit", "-m", "myBranch commit"])

        // master
        init.executeLocal("git checkout master")
        pom = new File(project, "pom.xml")
        processFileInplace(pom) { text ->
            text = text.replaceAll("1.0-SNAPSHOT", "1.2-SNAPSHOT")
        }
        init.executeLocal(["git", "commit", "-a", "-m", "master version change"])
        //        processFileInplace(pom) { text ->
        //            text = text.replace("${PROJECT_NAME}", "ProjMasterBranch")
        //        }
        //        init.executeLocal(["git", "commit", "-a", "-m", "master name change"])

        // merge
        Boolean inConflict = false
        try {
            init.executeLocal("git merge myBranch")
        } catch(GitflowMergeConflictException ex) {
            inConflict = true
            print init.gitMergeConflicts()
        }
        assert inConflict

//        if (System.properties['os.name'].toLowerCase().contains("mac os x")) {
//            def streeProcess = "/usr/local/bin/stree ${repoDir.getCanonicalPath()}".execute(null, repoDir)
//        }
        log.info "done"
    }

    def processFileInplace(file, Closure processText) {
        def text = file.text
        file.write(processText(text))
    }

    def cleanupSpec() {
        tempFolder.delete()
    }

    def "Fix pom conflicts"() {
        given: "FixPomMergeConflicts"
        FixPomMergeConflicts fpmc = new FixPomMergeConflicts(init:init)

        when: "call resolve Conflicts"
        //        fpmc.resolveConflicts()
        fpmc.resolveConflicts2()

        then: "version conflicts should be resolved"
        assert pom.exists()
        assert pom.text.contains("1.2-SNAPSHOT")
    }

}

