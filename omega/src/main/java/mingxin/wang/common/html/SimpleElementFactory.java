package mingxin.wang.common.html;

import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Copyright (c) 2017-2018 Mingxin Wang. All rights reserved.
 */

// Web页面常用静态元素工厂
public final class SimpleElementFactory {
    private static final DateTimeFormatter NORMAL_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");

    private SimpleElementFactory() {
    }

    public static String makeHeader(String title) {
        return "<!DOCTYPE html>" + System.lineSeparator()
                + StaticElementBuilder.of("head").addContent("<meta charset=\"UTF-8\">").addContent(StaticElementBuilder.of("title").addContent(title)).build();
    }

    public static String makeLabel(String name) {
        return StaticElementBuilder.of("label").addContent(name).build();
    }

    public static String makePostForm(String action, String... items) {
        StaticElementBuilder result = StaticElementBuilder.of("form")
                .addAttribute("action", action)
                .addAttribute("method", "post");
        for (String item : items) {
            result.addContent(makeParagraph(item));
        }
        return result.build();
    }

    public static String makeFilePostForm(String action, Iterable<Pair<String, String>> pairs) {
        StaticElementBuilder result = StaticElementBuilder.of("form")
                .addAttribute("action", action)
                .addAttribute("method", "post")
                .addAttribute("enctype", "multipart/form-data");
        for (Pair<String, String> pair : pairs) {
            result.addContent(makeParagraph(makeLabel(pair.getLeft()) + makeFileInput(pair.getRight())));
        }
        return result.build();
    }

    public static String makeLink(String display, String uri) {
        return makeLink(display, uri, false);
    }

    public static String makeLink(String display, String uri, boolean newWindow) {
        StaticElementBuilder result = StaticElementBuilder.of("a").addAttribute("href", uri);
        if (newWindow) {
            result.addAttribute("target", "_blank");
        }
        return result.addContent(display).build();
    }

    public static String makeLine() {
        return StaticElementBuilder.of("hr").build();
    }

    public static String makeParagraph(String content) {
        return StaticElementBuilder.of("p").addContent(content).build();
    }

    public static String makeText(String data) {
        return StaticElementBuilder.of("pre").addContent(
                data.replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")).build();
    }

    public static String makeDateInput(String name) {
        return makeInputBuilder("date", name, null, null).build();
    }

    public static String makeDateInput(String name, LocalDate defaultValue) {
        return makeInputBuilder("date", name, null, NORMAL_DATE_FORMAT.print(defaultValue)).build();
    }

    public static String makeFileInput(String name) {
        return makeInputBuilder("file", name, null, null).build();
    }

    public static String makeHiddenInput(String name, String placeholder, String value) {
        return makeInputBuilder("hidden", name, placeholder, value).build();
    }

    public static String makeTextInput(String name, String placeholder, String value) {
        return makeInputBuilder("text", name, placeholder, value).build();
    }

    public static String makeTextArea(String name, String placeholder, String value) {
        return makeInputBuilder("textarea", name, placeholder, null).addContent(value).build();
    }

    public static String makePasswordInput(String name) {
        return makeInputBuilder("password", name, null, null).build();
    }

    public static String makeSubmitButton(String name) {
        return makeInputBuilder("submit", null, null, name).build();
    }

    public static String makeRadioInput(String name, String selected, Iterable<? extends Pair<String, String>> options) {
        StringBuilder result = new StringBuilder();
        for (Pair<String, String> option : options) {
            StaticElementBuilder builder = makeInputBuilder("radio", name, null, option.getValue());
            if (option.getValue().equals(selected)) {
                builder.addAttribute("checked", "checked");
            }
            result.append(builder.build());
            result.append(option.getKey());
        }
        return result.toString();
    }

    public static String makeSelectInput(String name, String selected, Iterable<? extends Pair<String, String>> options) {
        StaticElementBuilder result = StaticElementBuilder.of("select").addAttribute("name", name);
        for (Pair<String, String> option : options) {
            StaticElementBuilder builder = StaticElementBuilder.of("option").addAttribute("value", option.getValue());
            if (option.getValue().equals(selected)) {
                builder.addAttribute("selected", "selected");
            }
            result.addContent(builder.addContent(option.getKey()));
        }
        return result.build();
    }

    private static StaticElementBuilder makeInputBuilder(String type, String name, String placeholder, String value) {
        StaticElementBuilder result = StaticElementBuilder.of("input").addAttribute("type", type);
        if (name != null) {
            result.addAttribute("name", name);
        }
        if (placeholder != null) {
            result.addAttribute("placeholder", placeholder);
        }
        if (value != null) {
            result.addAttribute("value", value);
        }
        return result;
    }
}