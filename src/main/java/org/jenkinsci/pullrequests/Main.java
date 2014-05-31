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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Generates https://wiki.jenkins-ci.org/display/JENKINS/Pending+Pull+Requests
 *
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

        String pageContent = toWikiPage(r);

        System.out.println(pageContent);

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

    private static List<Repository> groupByRepositories(List<GHPullRequest> prs) {
        Map<String,Repository> r = new HashMap<String, Repository>();
        for (GHPullRequest pr : prs) {
            String name = pr.getRepository().getName();
            Repository v = r.get(name);
            if (v==null)
                r.put(name,v=new Repository(name));
            v.count++;
        }

        List<Repository> list = new ArrayList<Repository>(r.values());
        Collections.sort(list);
        return list;
    }

    private static String toWikiPage(List<GHPullRequest> r) throws IOException {
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(page,true);

        IOUtils.copy(Main.class.getResourceAsStream("byRepo.txt"), page);
        for (Repository repo : groupByRepositories(r)) {
            String link = String.format("[%1$s|https://github.com/jenkinsci/%1$s/pulls]", repo.name);
            out.printf("|%60s|%4s|\n", link, repo.count);
        }

        final Date now = new Date();
        IOUtils.copy(Main.class.getResourceAsStream("unattended.txt"), page);
        for (GHPullRequest p : r) {
            final long daysSinceCreation = daysBetween(now, p.getCreatedAt());
            final long daysSinceUpdate = daysBetween(now, p.getUpdatedAt());
            boolean highlight = daysSinceCreation > 14;
            if (p==null || p.getRepository()==null || p.getUrl()==null)
                System.out.println(p);

            String userName;
            if (p.getUser()==null)
                userName = "-";
            else
                userName = p.getUser().getLogin();

            out.printf("|%30s|%20s|%5s|%5s|%s\n",
                    format(p.getRepository().getName(),highlight),
                    format(userName,highlight),
                    format(String.valueOf(daysSinceCreation),highlight),
                    format(String.valueOf(daysSinceUpdate),highlight),
                    format("[" + escape(p.getTitle()) + "|" + p.getUrl() + "]", highlight));
        }
        out.close();
        return page.toString();
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


    private static void writePendingPullRequestsWikiPage(Credentials creds, String page) throws ServiceException, IOException {
        ConfluenceSoapService service = Confluence.connect(new URL("https://wiki.jenkins-ci.org/"));
        String token = service.login(creds.username, creds.password);

        RemotePage p = service.getPage(token, "JENKINS", "Pending Pull Requests");
        p.setContent(page);
        service.storePage(token,p);
    }

    private static String escape(String title) {
        return title.replace("[","\\[").replace("]","\\]");
    }

    private static String format(String s, boolean important) {
        return important ? "{color:red}*"+s+"*{color}" : s;
    }
}
