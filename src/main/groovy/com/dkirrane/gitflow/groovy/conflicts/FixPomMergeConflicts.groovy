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
package com.dkirrane.gitflow.groovy.conflicts

import com.dkirrane.gitflow.groovy.GitflowInit
import com.dkirrane.gitflow.groovy.ex.GitflowException
import com.dkirrane.gitflow.groovy.ex.GitflowMergeConflictException
import groovy.util.logging.Slf4j

import org.apache.maven.artifact.versioning.ComparableVersion

/**
 * Script to resolve merge conflicts of version numbers in Maven POMs after a merge
 * This would always happen after merging a release branch back into develop
 *
 * Based on https://gist.github.com/brettporter/1723108/raw/368dd34131e2cec0874c99464f7453fe4ad7c07a/fix_maven_conflicts.rb
 */
@Slf4j
class FixPomMergeConflicts {

    @Delegate GitflowInit init
    def msgPrefix = ""
    def msgSuffix = ""

    enum State {
        NORMAL_LINE, OUR_LINE, THEIR_LINE
    }

    // (?ms) makes it multiline
    // ^<{7} matches conflict start marker
    // .*? non-greedy match
    // ^>{7} matches conflict end marker
    // [^\n]+ macthes any characters til the end of the line
    //    static final CONFLICT_REGEX = ~/(?ms)^<{7}.*?^>{7}[^\n]+/

    void resolveConflicts() throws GitflowException, GitflowMergeConflictException {

        def conflicts = init.gitMergeConflicts()

        def pomFiles = conflicts.findAll { it.name =~ /pom\.xml$/ }

        pomFiles.each(){ pom, index ->

            def fileTxt = pom.text
            def matcher = fileTxt =~ /(?ms)^<{7}.*?^>{7}[^\n]+/
            log.warn "Conflicts:"
            matcher.each {
                log.warn it
                String resolvedVersionConflict = parseVersionConflict(it)
                log.info "Resolved Version Conflict: ${resolvedVersionConflict}"
            }
        }
    }

    def String parseVersionConflict(text) {
        def matcher = text =~ /(?ms)^\s*<version>(.*?)<\/version>\s*$/

        log.debug "Versions:"

        def ourVersionTag = matcher[0][0]
        log.debug "Ours Tag "  + ourVersionTag
        def ourVersion = matcher[0][1]
        log.info "Ours [" + ourVersion + "]"

        def theirVersionTag = matcher[1][0]
        log.debug "Theirs Tag "  + theirVersionTag
        def theirVersion = matcher[1][1]
        log.info "Theirs ["  + theirVersion + "]"

        ComparableVersion ours = new ComparableVersion(ourVersion)
        log.debug "Our ComparableVersion [" + ours.toString() + "]"
        ComparableVersion theirs = new ComparableVersion(theirVersion)
        log.debug "Their ComparableVersion [" + theirs.toString() + "]"

        int compared = ours.compareTo(theirs)
        log.debug "Compared: " + compared
        if(compared >= 0){
            return ourVersionTag
        } else {
            return theirVersionTag
        }
    }

    void resolveConflicts2() throws GitflowException, GitflowMergeConflictException {
        log.debug "Checking for conflicts to resolve in ${init.repoDir}"

        def conflicts = init.gitMergeConflicts()        
        log.debug "Merge conflicts ${conflicts}"

        def pomFiles = conflicts.findAll { it.name =~ /pom\.xml$/ }
        log.debug "POM files ${pomFiles}"

        pomFiles.each() { pom ->

            def tmpFile = File.createTempFile('fixPomVersionConflicts', '.xml')
            log.info tmpFile.absolutePath

            List lines = pom.readLines()

            State state = State.NORMAL_LINE
            def conflict
            def outstandingConflicts = false

            lines.eachWithIndex { line, lineNo ->
                log.debug "${lineNo}: ${line}"

                if (line =~ /^<{7}/) {

                    if(state != State.NORMAL_LINE) {
                        throw new GitflowException("Encountered conflict start marker at line ${lineNo + 1}, but conflict at line ${conflict[lineNum]}")
                    }

                    state = State.OUR_LINE

                    conflict = [ lineNum: lineNo + 1 ]

                } else if (line =~ /^={7}/) {

                    if(state != State.OUR_LINE) {
                        throw new GitflowException("Encountered conflict separator at line ${lineNo}, but no conflict start marker found on previous lines")
                    }
                    state = State.THEIR_LINE

                } else if (line =~ /^>{7}/) {

                    if(state != State.THEIR_LINE) {
                        throw new GitflowException("Encountered conflict end marker at line ${lineNo}, but no conflict separator on previous lines")
                    }

                    def ourChange = conflict[State.OUR_LINE.name()]
                    def theirChange = conflict[State.THEIR_LINE.name()]

                    def leftVersion = parseVersion(ourChange)
                    log.debug "leftVersion = [" + leftVersion + "]"
                    def rightVersion = parseVersion(theirChange)
                    log.debug "rightVersion = [" + rightVersion + "]"

                    if (!leftVersion?.trim() || !rightVersion?.trim()) {
                        log.debug "Skipping conflict at line ${conflict[lineNum]} as it contains more than conflicting versions"

                        def format = formatConflict(conflict)
                        log.debug "Conflict " + format
                        tmpFile.append format + "\n"
                        outstandingConflicts = true
                    } else {
                        Integer compared = compareVersions(leftVersion, rightVersion)
                        if(compared >= 0){
                            log.debug "Resolved conflict using ours: " + ourChange
                            tmpFile.append ourChange + "\n"
                        } else {
                            log.debug "Resolved conflict using theirs: " + theirChange
                            tmpFile.append theirChange + "\n"
                        }
                    }

                    state = State.NORMAL_LINE
                } else {
                    if(state != State.NORMAL_LINE){
                        log.debug "State = ${state.name()}"
                        def key = state.name()
                        if(!conflict[key]) {
                            conflict[key] = ""
                        }
                        conflict[key] += line
                    }
                    else {
                        tmpFile.append line + "\n"
                    }
                }
            }

            log.debug "Temp file:\n" + tmpFile.text
            if(state != State.NORMAL_LINE) {
                throw new GitflowException("Unclosed conflict marker at line ${conflict[lineNo]}")
            }

            if (null != conflict) {
                pom.renameTo(new File(pom.parent, "pom.xml.orig"))
                tmpFile.renameTo(new File(pom.parent, "pom.xml"))
                if(!outstandingConflicts) {
                    init.executeLocal("git add ${pom.path}")
                    def resolveMsg = "${msgPrefix}Auto-resolve pom version conflicts${msgSuffix}"
                    init.executeLocal(["git", "commit", "-m", "\"${resolveMsg}\""])
                }
            } else {
                tmpFile.delete()
            }

        }
    }

    def String parseVersion(text) {
        def matcher = text =~ /^\s*<version>\s*(.*?)\s*<\/version>\s*$/

        log.debug "Versions:"

        def versionTag = null
        def version = null
        if(matcher[0]){
            versionTag = matcher[0][0]
            log.debug "Version Tag "  + versionTag
            version = matcher[0][1]
            log.debug "Version [" + version + "]"
        }

        return version
    }

    def Integer compareVersions(String ourVersion, String theirVersion) {
        ComparableVersion ours = new ComparableVersion(ourVersion)
        log.debug "Our ComparableVersion [" + ours.toString() + "]"
        ComparableVersion theirs = new ComparableVersion(theirVersion)
        log.debug "Their ComparableVersion [" + theirs.toString() + "]"

        int compared = ours.compareTo(theirs)
        log.debug "Compared: " + compared
        return compared
    }

    def String formatConflict(conflict){
        def format =\
            "<<<<<<<\n" +
            "${conflict[State.OUR_LINE.name()]}\n" +
            "=======\n" +
            "${conflict[State.THEIR_LINE.name()]}\n" +
            ">>>>>>>\n"
        return format
    }

}

