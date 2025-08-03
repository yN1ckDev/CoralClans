package it.mattiolservices.coralclans.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import it.mattiolservices.coralclans.bootstrap.CoralClans;
import it.mattiolservices.coralclans.services.manager.Manager;
import lombok.Getter;

import java.io.File;
import java.io.IOException;

@Getter
public class ConfigManager implements Manager {

    private static ConfigManager INSTANCE;

    private YamlDocument messages, config, storage;

    @Override
    public void start() {
        INSTANCE = this;
        load();
    }

    @Override
    public void stop() {
        INSTANCE = null;
    }

    public void loadMessages() {
        try {
            messages = YamlDocument.create(
                    new File(CoralClans.get().getPlugin().getDataFolder(), "messages.yml"),
                    getClass().getResourceAsStream("/messages.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );
            messages.update();
            messages.save();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void loadConfig() {
        try {
            config = YamlDocument.create(
                    new File(CoralClans.get().getPlugin().getDataFolder(), "config.yml"),
                    getClass().getResourceAsStream("/config.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );
            config.update();
            config.save();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public void loadStorage() {
        try {
            storage = YamlDocument.create(
                    new File(CoralClans.get().getPlugin().getDataFolder(), "storage.yml"),
                    getClass().getResourceAsStream("/storage.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT,
                    UpdaterSettings.builder().setVersioning(new BasicVersioning("config-version")).build()
            );
            storage.update();
            storage.save();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static void load() {
        CoralClans.get().getConfigManager().loadConfig();
        CoralClans.get().getConfigManager().loadMessages();
        CoralClans.get().getConfigManager().loadStorage();
    }

    public void reload() throws IOException {
        messages.reload();
        config.reload();
        storage.reload();
    }

    public static ConfigManager get() {
        return INSTANCE;
    }
}