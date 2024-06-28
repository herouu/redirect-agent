package io.github.herouu;

import cn.hutool.core.lang.Console;
import cn.hutool.core.lang.Dict;
import okhttp3.Dns;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomDns implements Dns {

    private String ymlPath;

    public CustomDns(String ymlPath) {
        this.ymlPath = ymlPath;
    }

    private List<String> resolve(String hostname) throws UnknownHostException, TextParseException {
        List<String> list = new ArrayList<>();
        Dict dict = AnotherClass.getYamlConfig(ymlPath);
        List<String> dnsServers = dict.getByPath("request.global.dns");
        // 设置DNS服务器地址
        SimpleResolver resolver = new SimpleResolver();
        for (String server : dnsServers) {
            resolver.setAddress(InetAddress.getByName(server));
            Lookup lookup = new Lookup(hostname, Type.A);
            lookup.setResolver(resolver);

            Record[] records = lookup.run();
            if (lookup.getResult() == Lookup.SUCCESSFUL) {
                for (Record record : records) {
                    Console.log("Hostname: {} Resolved IP: {}, using DNS: {}", hostname, record.rdataToString(), server);
                    list.add(record.rdataToString());
                }
            } else {
                Console.error("Hostname: {} Failed to resolve using {}: {}", hostname, server, lookup.getErrorString());
            }
        }
        return list;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<String> resolve = null;
        try {
            resolve = resolve(hostname);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
        return resolve.stream().map(item -> {
            try {
                return InetAddress.getByName(item);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }).distinct().collect(Collectors.toList());
    }
}