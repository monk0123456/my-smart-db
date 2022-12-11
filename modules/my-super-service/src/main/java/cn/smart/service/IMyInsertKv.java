package cn.smart.service;

import java.util.Hashtable;
import java.util.List;

public interface IMyInsertKv {
    //public Hashtable<String, Object> getInsertKv(String userToken, String sql);
    //public Object getInsertKvAgrs(String userToken, String sql, Object[] args);

    public List<Hashtable<String, Object>> getInsertKvAgrs(String userToken, String sql, Object[] args);
}
