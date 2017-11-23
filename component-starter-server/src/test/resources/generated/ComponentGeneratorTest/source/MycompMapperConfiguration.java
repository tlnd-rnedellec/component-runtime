package com.foo.source;

import java.io.Serializable;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;

@GridLayout({
    // the generated layout put one configuration entry per line,
    // customize it as much as needed
    @GridLayout.Row({ "name" })
})
public class MycompMapperConfiguration implements Serializable {
    @Option
    private String name;

    public String getName() {
        return name;
    }

    public MycompMapperConfiguration setName(String name) {
        this.name = name;
        return this;
    }
}