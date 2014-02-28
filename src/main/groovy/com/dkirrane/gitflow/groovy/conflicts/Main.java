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
package com.dkirrane.gitflow.groovy.conflicts;

import com.dkirrane.gitflow.groovy.GitflowInit;
import com.dkirrane.gitflow.groovy.Constants;
import com.dkirrane.gitflow.groovy.ex.GitflowException;
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException;
import java.io.File;

/**
 *
 */
public class Main {

    public static void main(String[] args) throws GitflowException, GitflowMergeConflictException {
        GitflowInit gitflowInit = new GitflowInit();
        gitflowInit.setRepoDir(new File("/Users/desmondkirrane/NetBeansProjects/GitHub/GroovyGitflow/ggitflow-test1"));
        gitflowInit.setMasterBrnName(Constants.DEFAULT_MASTER_BRN_NAME);
        gitflowInit.setDevelopBrnName("development");
        gitflowInit.setFeatureBrnPref(Constants.DEFAULT_FEATURE_BRN_PREFIX);
        gitflowInit.setReleaseBrnPref(Constants.DEFAULT_RELEASE_BRN_PREFIX);
        gitflowInit.setHotfixBrnPref(Constants.DEFAULT_HOTFIX_BRN_PREFIX);
        gitflowInit.setSupportBrnPref(Constants.DEFAULT_SUPPORT_BRN_PREFIX);
        gitflowInit.setVersionTagPref(Constants.DEFAULT_VERSION_TAG_PREFIX);
        
        FixPomMergeConflicts fixPomMergeConflicts = new FixPomMergeConflicts();
        fixPomMergeConflicts.setInit(gitflowInit);
        fixPomMergeConflicts.setMsgPrefix("");
        fixPomMergeConflicts.setMsgSuffix("");
        
        fixPomMergeConflicts.resolveConflicts2();
    }
}
