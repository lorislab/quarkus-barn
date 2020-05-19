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
package org.lorislab.quarkus.barn;

import io.vertx.mutiny.sqlclient.Pool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BarnTest {

    @Test
    public void barnConstructorTest() {
        Assertions.assertThrows(NullPointerException.class, () -> new Barn(null, null));
        Assertions.assertThrows(NullPointerException.class, () -> new Barn(new Pool(null), null));
        Assertions.assertThrows(IllegalStateException.class, () -> new Barn(new Pool(null), BarnConfig.builder().build()));
    }
}
