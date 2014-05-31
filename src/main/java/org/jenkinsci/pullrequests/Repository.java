package org.jenkinsci.pullrequests;

/**
 * @author Kohsuke Kawaguchi
 */
public class Repository implements Comparable<Repository> {
    final String name;
    int count;

    public Repository(String name) {
        this.name = name;
    }

    /**
     * We want descending sort, so comparing in the reverse order.
     */
    public int compareTo(Repository that) {
        return that.count - this.count;
    }
}
