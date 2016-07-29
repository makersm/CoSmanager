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
    void addVnidTable(int vnid, int cos);

    /**
     * delete vnid-cos table's tuple
     * @param vnid
     */
    void deleteVnidTable(int vnid);

    /**
     * get vnid-cos table's key set
     * @return keysset
     */
    Set<Integer> getVnidkeySet();

    /**
     * get vnid-cos table's value by key
     * @param vnid
     * @return
     */
    int getVnidValue(int vnid);
}
