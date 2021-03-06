/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.app.vtnrsc;

import java.util.Objects;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Immutable representation of a tenantNetwork identity.
 */
public final class TenantNetworkId {

    private final String networkid;

    // Public construction is prohibited
    private TenantNetworkId(String networkid) {
        this.networkid = networkid;
    }

    /**
     * Creates a tenantNetwork id using the networkid.
     *
     * @param networkid tenantnetwork String
     * @return NetworkId
     */
    public static TenantNetworkId networkId(String networkid) {
        checkNotNull(networkid, "Networkid cannot be null");
        return new TenantNetworkId(networkid);
    }

    /**
     *
     * @return tenantNetworkid
     */
    public String networkid() {
        return networkid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof TenantNetworkId) {
            final TenantNetworkId that = (TenantNetworkId) obj;
            return this.getClass() == that.getClass()
                    && Objects.equals(this.networkid, that.networkid);
        }
        return false;
    }

    @Override
    public String toString() {
        return networkid;
    }

}
