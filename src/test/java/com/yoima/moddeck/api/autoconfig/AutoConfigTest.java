package com.yoima.moddeck.api.autoconfig;

import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.option.IntegerOption;
import com.yoima.moddeck.storage.JsonConfigStorage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AutoConfigTest {
    @ModDeckAutoConfig(modId = "auto_test", titleKey = "auto_test.title")
    static final class Values {
        @AutoEntry(nameKey = "auto_test.enabled") boolean enabled = true;
        @AutoEntry(nameKey = "auto_test.count", order = 1) @AutoRange(min = 0, max = 10) int count = 3;
        @AutoEntry(nameKey = "auto_test.color", category = "look", categoryKey = "auto_test.look",
                categoryOrder = 2) @AutoColor int color = 0x112233;
        @AutoEntry(nameKey = "auto_test.names", category = "look", categoryKey = "auto_test.look",
                categoryOrder = 2) List<String> names = List.of("Alex");
        @AutoIgnore String internal = "hidden";
    }

    @Test void discoversFieldsAndSynchronizesOnSuccessfulSave(@TempDir Path directory) throws Exception {
        ConfigScreenApi.useStorage(new JsonConfigStorage(directory));
        Values values = new Values();
        AutoConfigHolder<Values> holder = AutoConfig.register(values);
        AtomicBoolean loaded = new AtomicBoolean();
        AtomicBoolean saved = new AtomicBoolean();
        holder.onLoad(ignored -> loaded.set(true)).onSave(ignored -> saved.set(true));
        assertEquals(2, holder.definition().categories().size());
        IntegerOption count = (IntegerOption) holder.definition().option("general", "count").orElseThrow();
        count.setDraftValue(7);
        assertEquals(3, values.count);
        ConfigScreenApi.save(holder.definition());
        assertEquals(7, values.count);
        assertTrue(loaded.get());
        assertTrue(saved.get());
    }
}
