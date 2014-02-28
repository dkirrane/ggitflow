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
package com.dkirrane.gitflow.groovy.origin.util

import groovy.util.ConfigSlurper;
import groovy.util.logging.Slf4j
import org.eclipse.egit.github.core.Authorization
import org.eclipse.egit.github.core.Repository
import org.eclipse.egit.github.core.User
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.service.OAuthService
import org.eclipse.egit.github.core.service.RepositoryService
import com.dkirrane.gitflow.groovy.ex.GitflowException

/**
 *
 */
@Slf4j
class GitHubUtil {

    static final String AUTH_NAME = "TestRepoAuth"    
    static final String REPO_NAME_PREFIX = "TestRepo"

    static void createRepo(File repoDir, String repoName) {
        def repositoryName = REPO_NAME_PREFIX + repoName

        def oauthAccessToken = getAuthToken()

        /**
         * Create test repository
         */
        RepositoryService repositoryService = new RepositoryService()
        repositoryService.getClient().setOAuth2Token(oauthAccessToken)

        //        List<Repository> repositories = repositoryService.getRepositories();
        //        repositories.each() { print "${it.name} \t\t ${it.sshUrl}\n" }; log.info "\n";

        Repository repo = repositoryService.getRepositories().find { it.name == repositoryName }
        if(null != repo) {
            deleteRepo()
        } 
        repo = new Repository()
        repo.setName(repositoryName);
        repo.setOwner(new User().setLogin("o"))
        repositoryService.createRepository(repo)
        repo = repositoryService.getRepositories().find { it.name == repositoryName }
        
        println "SSH URL ${repo.sshUrl}"
        println "HTTPS URL ${repo.cloneUrl}"

        /**
         * Clone repo
         */
        def cmd = ["git", "clone", "${repo.cloneUrl}", "${repoDir.path}"]

        StringBuilder standard = new StringBuilder(450000)
        StringBuilder error = new StringBuilder(450000)
        def process = cmd.execute(null, repoDir)
        process.waitForProcessOutput(standard, error)

        println "OUT: '" + standard + "'"
        println "WARN: '" + error + "'"
        println "Exit code: " + process.exitValue()
        if (process.exitValue()){
            println "ERROR: executing command: '${cmd}'"
            println error
            throw new GitflowException(error.toString())
        }
    }

    static void deleteRepo(String repoName) {
        def repositoryName = REPO_NAME_PREFIX + repoName

        def oauthAccessToken = getAuthToken()
        
        Properties props  = new Properties()
        props.load(GitHubUtil.class.getResourceAsStream("/github.properties"))
        def config = new ConfigSlurper().parse(props)
        /**
         * Delete test repository
         * http://developer.github.com/v3/repos/#delete-a-repository
         *
         * curl -X DELETE -H 'Authorization: token xxx' https://api.github.com/repos/:owner/:repo
         */
        GitHubClient client = new GitHubClient();
        client.setOAuth2Token(oauthAccessToken);
        client.delete("/repos/${config.username}/${repositoryName}")
    }

    static void getRemainingRequests() {
        def oauthAccessToken = getAuthToken()
        Properties props  = new Properties()
        props.load(GitHubUtil.class.getResourceAsStream("/github.properties"))
        def config = new ConfigSlurper().parse(props)

        GitHubClient client = new GitHubClient();
        client.setCredentials(config.username, config.password)

        /**
         * Remaining GitHub Requests
         */
        def remainignReqs = client.getRemainingRequests();
        println "Remaining GitHub Requests = " + remainignReqs
    }

    private static String getAuthToken(){
        //http://developer.github.com/libraries/
        Properties props  = new Properties()
        props.load(GitHubUtil.class.getResourceAsStream("/github.properties"))
        def config = new ConfigSlurper().parse(props)
        //        println "GitHub username = " + config.username

        /**
         * Create Authorization for creating and deleting test repositories
         */
        OAuthService oauthservice = new OAuthService()
        oauthservice.getClient().setCredentials(config.username, config.password)

        //        def authList = oauthservice.getAuthorizations()
        //        authList.each() { println " ${it.id} - ${it.token} - ${it.note} - ${it.scopes}\n" }; println "\n";

        Authorization auth = oauthservice.getAuthorizations().find { it.note == AUTH_NAME }
        if(null == auth){
            Authorization createAuth = new Authorization();
            createAuth.setNote(AUTH_NAME)
            createAuth.setScopes(Arrays.asList("user", "public_repo", "repo", "gist", "delete_repo"))
            oauthservice.createAuthorization(createAuth)
            println "Created Auth ${createAuth.note}"
            auth = oauthservice.getAuthorizations().find { it.note == AUTH_NAME }
        }
        def oauthAccessToken = auth.token
        //        println oauthAccessToken
        return oauthAccessToken;
    }
}

