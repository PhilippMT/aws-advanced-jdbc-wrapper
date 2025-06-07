package software.amazon.jdbc.plugin.aurora;

import java.util.Properties;
import software.amazon.jdbc.ConnectionPlugin;
import software.amazon.jdbc.ConnectionPluginFactory;
import software.amazon.jdbc.PluginService;

public class AuroraAdvancedFeaturesPluginFactory implements ConnectionPluginFactory {

  @Override
  public ConnectionPlugin getInstance(PluginService pluginService, Properties props) {
    // The AuroraAdvancedFeaturesPlugin constructor takes PluginService.
    // It internally uses pluginService.getProperties() for initial setup if needed,
    // and also processes properties passed during connect/initHostProvider.
    // The 'props' passed here to getInstance can be used by the plugin if its constructor
    // is adapted, or if specific factory-time configuration is needed.
    // Given the current constructor `AuroraAdvancedFeaturesPlugin(PluginService pluginService)`,
    // we directly use that. If the plugin needed these specific `props` at construction,
    // its constructor would need to accept them.

    // Let's assume the plugin constructor `public AuroraAdvancedFeaturesPlugin(PluginService pluginService)`
    // is the definitive one. The `props` argument here is part of the ConnectionPluginFactory interface.
    // We can pass these `props` to the plugin if we modify its constructor, or consider them as
    // supplemental/override if the plugin has a method to accept them after construction,
    // or rely on pluginService.getProperties() for the base set and props in connect() for specifics.

    // Current AuroraAdvancedFeaturesPlugin constructor is:
    // public AuroraAdvancedFeaturesPlugin(PluginService pluginService)
    // It uses pluginService.getProperties() for its initial preferredAz.
    // The `props` parameter in `getInstance` might contain additional configurations
    // specific to this plugin instance, beyond what's in the general URL or `pluginService.getProperties()`.
    // For now, sticking to the defined constructor. If `props` are essential for the plugin's
    // construction beyond what `pluginService.getProperties()` provides, the plugin's constructor should be updated.
    // For maximum flexibility, a plugin could take both pluginService and these specific props.
    // Let's adjust the plugin to accept these props if they are meant for its direct construction.
    // Re-checking the plugin: It has `public AuroraAdvancedFeaturesPlugin(PluginService pluginService)`
    // I'll stick to that. The `props` here are often the same as `pluginService.getProperties()`.
    return new AuroraAdvancedFeaturesPlugin(pluginService);
  }
}
