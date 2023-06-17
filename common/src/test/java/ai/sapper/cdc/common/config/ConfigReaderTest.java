package ai.sapper.cdc.common.config;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigReaderTest {
    public enum Type {
        File, Directory, Unknown
    }

    @Getter
    @Setter
    public static class TestSettings extends Settings {
        @Config(name = "name")
        private String name;
        @Config(name = "created", type = Long.class)
        private long createDate;
        @Config(name = "version", type = Float.class)
        private float version;
        @Config(name = "type", type = Type.class)
        private Type type;
        @Config(name = "authors", type = List.class)
        private List<String> authors;
        @Config(name = "domains", type = Map.class)
        private Map<String, String> domains;
        @Config(name = "int-list", type = List.class)
        private List<Integer> intValues;
        @Config(name = "test.class", type = Class.class)
        private Class<? extends ConfigReaderTest> testClass;
    }

    private static final String __CONFIG_FILE = "src/test/resources/config-test.xml";
    private static final String __CONFIG_PATH = "test";
    private static XMLConfiguration xmlConfiguration = null;

    @BeforeAll
    public static void setup() throws Exception {
        xmlConfiguration = TestUtils.readFile(__CONFIG_FILE);
        Preconditions.checkState(xmlConfiguration != null);
    }

    @Test
    void read() {
        try {
            ConfigReader reader = new ConfigReader(xmlConfiguration, __CONFIG_PATH, TestSettings.class);
            reader.read();
            TestSettings settings = (TestSettings) reader.settings();
            assertNotNull(settings);
            assertNotNull(settings.type);
            assertNotNull(settings.testClass);
            assertNotNull(settings.domains);
            assertFalse(settings.domains.isEmpty());
            assertNotNull(settings.intValues);
            assertFalse(settings.intValues.isEmpty());
        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex);
        }
    }

}