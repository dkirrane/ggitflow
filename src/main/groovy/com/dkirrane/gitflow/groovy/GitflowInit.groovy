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

import static com.dkirrane.gitflow.groovy.Constants.*
import com.dkirrane.gitflow.groovy.ex.GitflowException
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

/**
 *
 */
@InheritConstructors
@Slf4j
class GitflowInit extends GitflowCommon {

    def masterBrnName
    def developBrnName
    def featureBrnPref
    def releaseBrnPref
    def hotfixBrnPref
    def supportBrnPref
    def versionTagPref
    def push = true

    void cmdDefault() throws GitflowException {
        super.requireGitRepo()

        if(super.gitflowIsInitialized()) {
            log.info "Gitflow is already initialized"
            //            log.info "WARN To force reinitialization, use: git flow init -f"
            return
        }

        masterBrnName = masterBrnName?.trim() ?: DEFAULT_MASTER_BRN_NAME
        developBrnName = developBrnName?.trim() ?: DEFAULT_DEVELOP_BRN_NAME
        featureBrnPref = featureBrnPref?.trim() ?: DEFAULT_FEATURE_BRN_PREFIX
        releaseBrnPref = releaseBrnPref?.trim() ?: DEFAULT_RELEASE_BRN_PREFIX
        hotfixBrnPref = hotfixBrnPref?.trim() ?: DEFAULT_HOTFIX_BRN_PREFIX
        supportBrnPref = supportBrnPref?.trim() ?: DEFAULT_SUPPORT_BRN_PREFIX
        versionTagPref = versionTagPref?.trim() ?: DEFAULT_VERSION_TAG_PREFIX

        log.info "Initializing Gitflow for repo '${repoDir}'."
        log.info "Gitflow Config:"
        log.info "\t Master branch '${masterBrnName}'"
        log.info "\t Develop branch '${developBrnName}'"
        log.info "\t Feature branch prefix '${featureBrnPref}'"
        log.info "\t Release branch prefix '${releaseBrnPref}'"
        log.info "\t Hotfix branch prefix '${hotfixBrnPref}'"
        log.info "\t Support branch prefix '${supportBrnPref}'"
        log.info "\t Version tag prefix '${versionTagPref}'"

        def origin = super.getOrigin()
        log.info "\t Origin = '${origin}'"

        // add a master branch if no such branch exists yet
        def masterBranch = super.getMasterBranch()
        if( masterBranch?.trim() ) {
            log.debug "WARN master branch '${masterBranch}' already configured."
        } else {
            if(super.gitAllBranches().size() == 0){
                log.debug "No branches exist yet. Master branch must be created now."
                super.executeLocal("git config gitflow.branch.master ${masterBrnName}")
            } else if(super.gitLocalBranchExists(masterBrnName)) {
                log.debug "Local master branch exists."
                super.executeLocal("git config gitflow.branch.master ${masterBrnName}")
            } else if(origin && super.gitRemoteBranchExists("${origin}/${masterBrnName}")) {
                log.debug "Remote master branch exists."
                super.executeLocal("git config gitflow.branch.master ${masterBrnName}")
            } else {
                log.error ""
                log.error "Gitflow is not configured but branches exist:"
                log.error super.gitAllBranches()

                log.error "Run this command to set the branch used for bringing forth production releases:"
                log.error "git config gitflow.branch.master <branch_name>"
                throw new GitflowException("You need to configure the master branch 'git config gitflow.branch.master <branch_name>'")
            }
        }

        // add a develop branch if no such branch exists yet
        def developBranch = super.getDevelopBranch()
        if( developBranch?.trim() ) {
            log.debug "WARN develop branch '${developBranch}' already configured."
        } else {
            if(super.gitAllBranches().minus(["${masterBrnName}","${origin}/${masterBrnName}"]).size() == 0){
                log.debug "No branches exist yet. Base branches must be created now."
                super.executeLocal("git config gitflow.branch.develop ${developBrnName}")
            } else if(super.gitLocalBranchExists(developBrnName)) {
                log.debug "Local develop branch exists."
                super.executeLocal("git config gitflow.branch.develop ${developBrnName}")
            } else if(origin && super.gitRemoteBranchExists("${origin}/${developBrnName}")) {
                log.debug "Remote develop branch exists."
                super.executeLocal("git config gitflow.branch.develop ${developBrnName}")
            } else {
                log.error ""
                log.error "Gitflow is not configured but branches exist:"
                log.error super.gitAllBranches()

                log.error  "Run this command to set the branch used for development of the next release:"
                log.error  "git config gitflow.branch.develop <branch_name>"
                throw new GitflowException("You need to configure the develop branch 'git config gitflow.branch.develop <branch_name>'")
            }
        }

        // Create master branch if needed
        if(super.gitLocalBranchExists(masterBrnName)) {
            log.debug "Local master branch already exists."
        } else if(origin && super.gitRemoteBranchExists("${origin}/${masterBrnName}")) {
            log.debug "Remote master branch already exists. Checking out."
            super.executeRemote("git checkout -b ${masterBrnName} --track ${origin}/${masterBrnName}")
        } else {
            log.debug "Creating master branch."
            // Create HEAD so we can create new branches in a new repo. This also creates master branch
            def head = super.executeLocal("git rev-parse --quiet --verify HEAD", true)
            if(head?.trim()) {
                log.debug head
            } else {
                super.executeLocal("git symbolic-ref HEAD refs/heads/${masterBrnName}")

                super.executeLocal(["git", "commit", "--allow-empty", "-q", "-m", "Initial Commit"])

                def rps = super.gitAllBranches()
                log.info "Master branch: " + rps
            }

            if(push && origin) {
                log.debug "Pushing master branch to ${origin}."
                // http://stackoverflow.com/questions/5343068/is-there-a-way-to-skip-password-typing-when-using-https-github
                // http://stackoverflow.com/questions/6031214/git-how-to-use-netrc-file-on-windows-to-save-user-and-password
                Integer exitCode = super.executeRemote("git push ${origin} ${masterBrnName}")
                if(exitCode){
                    def errorMsg
                    if (System.properties['os.name'].toLowerCase().contains("windows")) {
                        errorMsg = "Issue pushing feature branch '${masterBrnName}' to '${origin}'. URL[${super.getOriginURL()}]. Please ensure your username and password is in your ~/_netrc file"
                    } else {
                        errorMsg = "Issue pushing feature branch '${masterBrnName}' to '${origin}'. URL[${super.getOriginURL()}]. Please ensure your username and password is in your ~/.netrc file"
                    }
                    throw new GitflowException(errorMsg)
                }
            }
        }

        // Create develop branch if needed
        if(super.gitLocalBranchExists(developBrnName)) {
            log.debug "Local develop branch already exists."
        } else if(origin && super.gitRemoteBranchExists("${origin}/${developBrnName}")) {
            log.debug "Remote develop branch already exists. Checking out."
            super.executeRemote("git checkout -b ${developBrnName} --track ${origin}/${developBrnName}")
        } else {
            log.debug "Creating develop branch off of master"
            super.executeLocal("git checkout -q -b ${developBrnName} ${masterBrnName}")

            def rps = super.gitAllBranches()
            log.info "Branch: " + rps

            if(push && origin) {
                log.debug "Pushing develop branch to ${origin}."
                Integer exitCode = super.executeRemote("git push ${origin} ${developBrnName}")
                if(exitCode){
                    def errorMsg
                    if (System.properties['os.name'].toLowerCase().contains("windows")) {
                        errorMsg = "Issue pushing feature branch '${developBrnName}' to '${origin}'. URL[${super.getOriginURL()}]. Please ensure your username and password is in your ~/_netrc file"
                    } else {
                        errorMsg = "Issue pushing feature branch '${developBrnName}' to '${origin}'. URL[${super.getOriginURL()}]. Please ensure your username and password is in your ~/.netrc file"
                    }
                    throw new GitflowException(errorMsg)
                }
            }
        }

        // Feature branches
        def feature = super.getFeatureBranchPrefix()
        if(feature?.trim()) {
            log.debug feature
        } else {
            super.executeLocal("git config gitflow.prefix.feature ${featureBrnPref}")
        }

        // Release branches
        def release = super.getReleaseBranchPrefix()
        if(release?.trim()) {
            log.debug release
        } else {
            super.executeLocal("git config gitflow.prefix.release ${releaseBrnPref}")
        }

        // Hotfix branches
        def hotfix = super.getHotfixBranchPrefix()
        if(hotfix?.trim()) {
            log.debug hotfix
        } else {
            super.executeLocal("git config gitflow.prefix.hotfix ${hotfixBrnPref}")
        }

        // Support branches
        def support = super.getSupportBranchPrefix()
        if(support?.trim()) {
            log.debug support
        } else {
            super.executeLocal("git config gitflow.prefix.support ${supportBrnPref}")
        }

        // Version tag prefix
        def versiontag = super.getVersionTagPrefix()
        if(versiontag?.trim()) {
            log.debug versiontag
        } else {
            if(versionTagPref?.trim()) {
                super.executeLocal(["git", "config", "gitflow.prefix.versiontag", "${versionTagPref}"])
            } else{
                super.executeLocal(["git", "config", "gitflow.prefix.versiontag", ""])
            }
            super.executeLocal("git config --get-regexp gitflow.*")
        }
    }
}

