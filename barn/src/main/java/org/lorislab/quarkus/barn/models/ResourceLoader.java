/*
 * Copyright 2020 lorislab.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lorislab.quarkus.barn.models;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public class ResourceLoader {

    public static Resource createFrom(String item) {
        if (item == null) {
            throw new IllegalArgumentException("The migration item is null!");
        }
        int i2 = item.lastIndexOf(".sql");
        if (i2 == -1) {
            throw new IllegalArgumentException("The migration item does not ends with '.sql'");
        }
        int i1 = item.lastIndexOf("/");

        Resource result = new Resource();
        result.script = item;

        String name = item.substring(i1 + 1, i2);
        String prefix = name.substring(0, 1);
        if ("V".equals(prefix)) {
            result.repeatable = false;
        } else if ("R".equals(prefix)) {
            result.repeatable = true;
        } else {
            throw new IllegalArgumentException("Wrong prefix of the migration. Values: [V, R]. Found: " + prefix);
        }

        name = name.substring(1);
        String[] ver_desc = name.split("__");
        result.description = ver_desc[1].replaceAll("_", " ");
        if (result.repeatable) {
            result.version = null;
        } else {
            result.version = ver_desc[0];
        }
        return result;
    }

    public static long checksum(byte[] value) {
        if (value == null || value.length <= 0) {
            return 0;
        }
        CRC32 crc = new CRC32();
        crc.update(value);
        return crc.getValue();
    }

    public static byte[] loadResourceContent(String script) {
        try {
            String tmp = script;
            if (!tmp.startsWith("/")) {
                tmp = "/" + tmp;
            }
            try (InputStream in = ResourceLoader.class.getResourceAsStream(tmp)) {
                if (in != null) {
                    return in.readAllBytes();
                }
                return null;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error read the migration resource " + script);
        }
    }

    public static String loadResource(String script) {
        byte[] data = loadResourceContent(script);
        if (data == null || data.length <= 0) {
            return null;
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
