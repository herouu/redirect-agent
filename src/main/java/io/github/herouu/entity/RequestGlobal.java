package io.github.herouu.entity;

import java.util.List;

public class RequestGlobal {

    private String domain;

    private List<String> dns;

    private String proxy;

    private List<String> unproxy;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<String> getDns() {
        return dns;
    }

    public void setDns(List<String> dns) {
        this.dns = dns;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = proxy;
    }

    public List<String> getUnproxy() {
        return unproxy;
    }

    public void setUnproxy(List<String> unproxy) {
        this.unproxy = unproxy;
    }
}
