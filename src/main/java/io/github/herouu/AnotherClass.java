package io.github.herouu;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.IterUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Console;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.yaml.YamlUtil;
import feign.okhttp.OkHttpClient;
import io.github.herouu.entity.RequestGlobal;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 *
 * </p>
 *
 * @author herouu
 * @since 2024-06-20
 */
public class AnotherClass {


    private static final Map<String, Dict> YAML_CONFIG = new ConcurrentHashMap<>();

    public static String exec(String ymlPath, Object attr, String url) {
        Map<String, Object> attributes;
        if (attr instanceof Map) {
            attributes = (Map<String, Object>) attr;
        } else {
            attributes = new HashMap<>();
        }
        if (StrUtil.isEmpty(ymlPath)) {
            Console.error("configPath is empty! return url:{}", url);
            return url;
        }
        Dict dict = getYamlConfig(ymlPath);
        List<Map<String, Object>> routes = dict.getByPath("spring.cloud.gateway.routes");
        String domain = dict.getByPath("request.global.domain");

        String service = ObjectUtil.defaultIfEmpty(Convert.toStr(attributes.get("name")), Convert.toStr(attributes.get("value")));
        String finalUrl = buildUrl(domain, service, url, routes);
        Console.log("exec服务:{} 原始url:{} 转换后url:{}", service, url, finalUrl);
        return finalUrl;
    }


    public static String currentUrl(String ymlPath, String service, String url) {
        if (StrUtil.isEmpty(ymlPath)) {
            Console.error("configPath is empty! return url:{}", url);
            return url;
        }
        Dict dict = getYamlConfig(ymlPath);
        List<Map<String, Object>> routes = dict.getByPath("spring.cloud.gateway.routes");
        String domain = dict.getByPath("request.global.domain");
        String finalUrl = replaceDomain(url, domain, service, routes);
        Console.log("currentUrl服务:{} 原始url:{} 转换后url:{}", service, url, finalUrl);
        return finalUrl;
    }

    /**
     * buildUrl
     *
     * @param domain     domain
     * @param name       name
     * @param defaultUrl defaultUrl
     * @param routes     routes
     * @return String
     */
    private static String buildUrl(String domain, String name, String defaultUrl, List<Map<String, Object>> routes) {
        Map<String, Pair<String, String>> map = resolveData(routes);
        if (map.containsKey(name)) {
            String url = Convert.toStr(map.get(name).getKey());
            String local = Convert.toStr(map.get(name).getValue());
            if (StrUtil.isNotEmpty(local)) {
                return local;
            }
            if (StrUtil.endWith(domain, "/")) {
                return StrUtil.removeSuffix(domain, "/") + url;
            } else {
                return domain + url;
            }
        }

        return defaultUrl;
    }

    public static Dict getYamlConfig(String ymlPath) {
        Dict dict;
        if (Objects.isNull(dict = YAML_CONFIG.get(ymlPath))) {
            refreshYamlConfig(ymlPath);
            dict = YAML_CONFIG.get(ymlPath);
        }
        return dict;
    }

    public static void refreshYamlConfig(String ymlPath) {
        Dict dict = YamlUtil.loadByPath(ymlPath);
        YAML_CONFIG.put(ymlPath, dict);
    }

    private static String replaceDomain(String url, String domain, String service, List<Map<String, Object>> routes) {
        URI asUri = URI.create(url);
        String host = asUri.getHost();
        int port = asUri.getPort();
        Map<String, Pair<String, String>> map = resolveData(routes);
        Pair<String, String> kv = map.get(service);
        if (Objects.nonNull(kv)) {
            if (StrUtil.isNotEmpty(kv.getValue())) {
                String key = kv.getKey();
                String result = asUri.getSchemeSpecificPart().replaceFirst("//", StrUtil.EMPTY).replaceFirst(host, StrUtil.EMPTY)
                        .replaceFirst(":" + Convert.toStr(port), StrUtil.EMPTY)
                        .replaceFirst(key, StrUtil.EMPTY);
                return kv.getValue() + result;
            }
        }
        return url.replaceFirst(host, URI.create(domain).getSchemeSpecificPart().replaceFirst("//", ""));
    }


    private static Map<String, Pair<String, String>> resolveData(List<Map<String, Object>> routes) {
        Map<String, Pair<String, String>> map = new HashMap<>();
        for (Map<String, Object> route : routes) {
            String uri = Convert.toStr(route.get("uri"));
            String local = Convert.toStr(route.get("local"));
            String key = StrUtil.replace(uri, "lb://", StrUtil.EMPTY);
            Object o = route.get("predicates");

            if (o instanceof List) {
                List<?> predicates = (List<?>) o;
                String first = Convert.toStr(IterUtil.getFirst(predicates));
                String url = StrUtil.replace(first, "Path=", StrUtil.EMPTY).replace("/**", StrUtil.EMPTY);
                map.put(key, Pair.of(url, local));
            }
        }
        return map;
    }


    public static OkHttpClient newOkHttpClient(String configFile) {
        Dict dict = getYamlConfig(configFile);
        CustomDns customDns = new CustomDns(configFile);
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
        RequestGlobal rg = dict.getByPath("request.global", RequestGlobal.class);
        if (CollUtil.isNotEmpty(rg.getDns())) {
            builder.dns(customDns);
        }
        if (StrUtil.isNotBlank(rg.getProxy())) {
            String proxyAddr = rg.getProxy();
            Proxy proxy = null;
            if (StrUtil.startWith(proxyAddr, "socks5://")) {
                String p = StrUtil.replace(proxyAddr, "socks5://", StrUtil.EMPTY);
                String[] addrAndPort = StrUtil.splitToArray(p, ":");
                proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(addrAndPort[0], Integer.parseInt(addrAndPort[1])));
            } else if (StrUtil.startWith(proxyAddr, "http://")) {
                String p = StrUtil.replace(proxyAddr, "http://", StrUtil.EMPTY);
                String[] addrAndPort = StrUtil.splitToArray(p, ":");
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(addrAndPort[0], Integer.parseInt(addrAndPort[1])));
            }
            Proxy finalProxy = proxy;
            if (Objects.nonNull(finalProxy)) {
                ProxySelector customProxySelector = new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        // 如果uri的主机名是localhost，则直接返回不使用代理
                        if ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())) {
                            return ListUtil.toList(Proxy.NO_PROXY);
                        }
                        if (CollUtil.contains(rg.getUnproxy(), uri.getHost())) {
                            return ListUtil.toList(Proxy.NO_PROXY);
                        }
                        // 否则使用代理服务器
                        return ListUtil.toList(finalProxy);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                        System.out.println("Connection failed: " + uri + " via " + sa);
                    }
                };
                builder.proxySelector(customProxySelector);
            }
        }

        okhttp3.OkHttpClient build = builder.build();
        return new OkHttpClient(build);
    }

}
