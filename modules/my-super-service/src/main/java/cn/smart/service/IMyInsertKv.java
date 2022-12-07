package cn.smart.service;

import java.util.Hashtable;

public interface IMyInsertKv {
    //public Hashtable<String, Object> getInsertKv(String userToken, String sql);
    //public Object getInsertKvAgrs(String userToken, String sql, Object[] args);

    public Hashtable<String, Object> getInsertKvAgrs(String userToken, String sql, Object[] args);
}
