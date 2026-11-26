package com.city.glam.common.message;

import java.util.function.UnaryOperator;

/**
 * The screen a request comes from: application / module / page / component codes, sent by the client in the headers
 * {@code X-Application}, {@code X-Module}, {@code X-Page} and {@code X-Component}. Codes are compared case-insensitively.
 */
public record Screen(String application, String module, String page, String component) {

    public static final String HEADER_APPLICATION = "X-Application";
    public static final String HEADER_MODULE = "X-Module";
    public static final String HEADER_PAGE = "X-Page";
    public static final String HEADER_COMPONENT = "X-Component";

    /** Shared texts: codes a screen does not define itself are looked up here. */
    public static final Screen COMMON = new Screen("ORBIT", "COMMON", "COMMON", "COMMON");

    public Screen {
        application = normalize(application);
        module = normalize(module);
        page = normalize(page);
        component = normalize(component);
    }

    /** The screen of a request, or {@code null} when one of the four headers is missing (then COMMON is used). */
    public static Screen fromHeaders(UnaryOperator<String> header) {
        Screen screen = new Screen(header.apply(HEADER_APPLICATION), header.apply(HEADER_MODULE),
                header.apply(HEADER_PAGE), header.apply(HEADER_COMPONENT));
        return screen.isComplete() ? screen : null;
    }

    public boolean isComplete() {
        return application != null && module != null && page != null && component != null;
    }

    /** {@code ORBIT/MASTER_DATA/CONTINENT/CONTINENT_FORM}. */
    public String path() {
        return application + "/" + module + "/" + page + "/" + component;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
