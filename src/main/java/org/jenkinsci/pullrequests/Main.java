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
    public static void main(String[] args) throws IOException, ServiceException {
        GitHub gitHub = GitHub.connect();

        List<GHPullRequest> r = gitHub.getOrganization("jenkinsci").getPullRequests();
        Collections.sort(r, new Comparator<GHPullRequest>() {
            public int compare(GHPullRequest lhs, GHPullRequest rhs) {
                return lhs.getUpdatedAt().compareTo(rhs.getUpdatedAt());
            }
        });

        ByteArrayOutputStream page = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(page,true);

        final long now = new Date().getTime();
        IOUtils.copy(Main.class.getResourceAsStream("preamble.txt"), page);
        for (GHPullRequest p : r) {
            final long days = TimeUnit.MILLISECONDS.toDays(now - p.getUpdatedAt().getTime());
            boolean highlight = days > 14;
            out.printf("|%30s|%20s|%5s|%s\n",
                    format(p.getRepository().getName(),highlight),
                    format(p.getUser().getLogin(),highlight),
                    format(String.valueOf(days),highlight),
                    format("[" + escape(p.getTitle()) + "|" + p.getUrl() + "]", highlight));
        }
        out.close();
        System.out.write(page.toByteArray());

        ConfluenceSoapService service = Confluence.connect(new URL("https://wiki.jenkins-ci.org/"));

        Properties props = new Properties();
        File credential = new File(new File(System.getProperty("user.home")), ".jenkins-ci.org");
        if (!credential.exists())
            throw new IOException("You need to have userName and password in "+credential);
        props.load(new FileInputStream(credential));
        String token = service.login(props.getProperty("userName"),props.getProperty("password"));

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
