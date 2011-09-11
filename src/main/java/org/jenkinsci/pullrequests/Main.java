package org.jenkinsci.pullrequests;

import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemotePage;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.confluence.Confluence;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GitHub;

import javax.xml.rpc.ServiceException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class Main {
    private static class Credentials {
        private String username;
        private String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public static void main(String[] args) throws IOException, ServiceException {
        List<GHPullRequest> r = retrieveGitHubPullRequestsOldestFirst();

        ByteArrayOutputStream pageContent = toWikiPage(r);

        System.out.write(pageContent.toByteArray());

        Credentials credentials = loadWikiCredentials();

        writePendingPullRequestsWikiPage(credentials, pageContent);
    }

    private static List<GHPullRequest> retrieveGitHubPullRequestsOldestFirst() throws IOException {
        GitHub gitHub = GitHub.connect();
        List<GHPullRequest> r = gitHub.getOrganization("jenkinsci").getPullRequests();
        Collections.sort(r, new Comparator<GHPullRequest>() {
            public int compare(GHPullRequest lhs, GHPullRequest rhs) {
                return lhs.getCreatedAt().compareTo(rhs.getCreatedAt());
            }
        });
        return r;
    }

    private static ByteArrayOutputStream toWikiPage(List<GHPullRequest> r) throws IOException {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(page,true);

        final Date now = new Date();
        IOUtils.copy(Main.class.getResourceAsStream("preamble.txt"), page);
        for (GHPullRequest p : r) {
            final long daysSinceCreation = daysBetween(now, p.getCreatedAt());
            final long daysSinceUpdate = daysBetween(now, p.getIssueUpdatedAt());
            boolean highlight = daysSinceCreation > 14;
            out.printf("|%30s|%20s|%5s|%5s|%s\n",
                    format(p.getRepository().getName(),highlight),
                    format(p.getUser().getLogin(),highlight),
                    format(String.valueOf(daysSinceCreation),highlight),
                    format(String.valueOf(daysSinceUpdate),highlight),
                    format("[" + escape(p.getTitle()) + "|" + p.getUrl() + "]", highlight));
        }
        out.close();
        return page;
    }
    
    private static long daysBetween(Date day1, Date day2) {
        return TimeUnit.MILLISECONDS.toDays(day1.getTime() - day2.getTime());
    }

    private static Credentials loadWikiCredentials() throws IOException {
        File credential = new File(new File(System.getProperty("user.home")), ".jenkins-ci.org");
        if (!credential.exists())
            throw new IOException("You need to have userName and password in "+credential);

        Properties props = new Properties();
        props.load(new FileInputStream(credential));
        return new Credentials(props.getProperty("userName"), props.getProperty("password"));
    }


    private static void writePendingPullRequestsWikiPage(Credentials creds, ByteArrayOutputStream page) throws ServiceException, IOException {
        ConfluenceSoapService service = Confluence.connect(new URL("https://wiki.jenkins-ci.org/"));
        String token = service.login(creds.username, creds.password);

        RemotePage p = service.getPage(token, "JENKINS", "Pending Pull Requests");
        p.setContent(page.toString());
        service.storePage(token,p);
    }

    private static String escape(String title) {
        return title.replace("[","\\[").replace("]","\\]");
    }

    private static String format(String s, boolean important) {
        return important ? "{color:red}*"+s+"*{color}" : s;
    }
}
