package org.iris4sdn.csdncm.cosmanager;

import java.util.Set;
/**
 * Created by gurum on 16. 7. 27.
 */
public interface CoSService {
    /**
     * add vnid-cos table's tuple
     * @param vnid
     * @param cos
     */
    void addVnidTable(String vnid, String cos);

    /**
     * delete vnid-cos table's tuple
     * @param vnid
     */
    void deleteVnidTable(String vnid);

    /**
     * get vnid-cos table's key set
     * @return keysset
     */
    Set<String> getVnidkeySet();

    /**
     * get vnid-cos table's value by key
     * @param vnid
     * @return
     */
    String getVnidValue(String vnid);
}
