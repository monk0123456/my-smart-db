package org.tools;

import clojure.lang.RT;
import cn.myservice.MyPlusFuncService;
import cn.mysuper.service.IMyPlusFunc;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.ignite.Ignition;
import org.gridgain.dml.util.MyCacheExUtil;
import org.gridgain.internal.h2.tools.SimpleResultSet;
import org.gridgain.myservice.MyPlusFuncImpl;
import org.gridgain.plus.dml.MySmartSql;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.List;

public class MyPlusFunc implements Serializable {

    private static final long serialVersionUID = 175563786053447344L;
    private static IMyPlusFunc myPlusFunc = MyPlusFuncImpl.getInstance();
    private static Gson gson = (new GsonBuilder()).enableComplexMapKeySerialization().setDateFormat("yyyy-MM-dd HH:mm:ss").create();

    /**
     * 获取 table 的自增长
     * */
    public static Object auto_id(final String tableName) {
        return Ignition.ignite().atomicSequence(tableName, 0, true).incrementAndGet();
    }

    /**
     * 获取序列的第 n 个
     * */
    public static Object nth(final Object coll, final int n) {
        return RT.nth(coll, n);
    }

    /**
     * 获取序列的第一个
     * */
    public static Object first(final Object lst) {
        return RT.first(lst);
    }

    /**
     * 显示信息
     * */
    public static String showMsg(final String msg)
    {
        return msg;
    }

    /**
     * 获取定时任务详细信息
     * */
    public static String getScheduler(final String schedulerName)
    {
        return myPlusFunc.getScheduler(schedulerName);
    }

    /**
     * 自定义函数的要求
     * 1、必须有一个默认构造函数
     * 2、方法名不能重载
     * */
    public static List myFun(final String methodName, final Object... ps) {
        return myPlusFunc.myFun(methodName, ps);
    }
//     public static Object myFun(final String methodName, final String... ps) {
//         return myPlusFunc.myFun(methodName, ps);
//     }
//    public static String myFun(final String methodName, final String... ps) {
//        return MyConvertUtil.ConvertToString(myPlusFunc.myFun(methodName, ps));
//    }

    /**
     * 场景调用
     * */
//    public static Object myInvoke(final String methodName, final Long group_id, final Object... ps)
//    {
//        return myPlusFunc.myInvoke(methodName, group_id, ps);
//    }
    public static List myInvoke(final String methodName, final String user_token, final Object... ps)
    {
        return myPlusFunc.myInvoke(methodName, user_token, ps);
        //return MyConvertUtil.ConvertToString(myPlusFunc.myInvoke(methodName, user_token, ps));
    }
//    public static String myInvoke(final String methodName, final String group_id, final String... ps)
//    {
//        return MyConvertUtil.ConvertToString(myPlusFunc.myInvoke(methodName, group_id, ps));
//    }

    /**
     * 联接函数的调用
     * */
//    public static Object myInvokeLink(final String methodName, final Long group_id, final Object... ps)
//    {
//        return myPlusFunc.myInvokeLink(methodName, group_id, ps);
//    }
    public static List myInvokeLink(final String methodName, final String user_token, final Object... ps)
    {
        return myPlusFunc.myInvokeLink(methodName, user_token, ps);
    }
//    public static String myInvokeLink(final String methodName, final String group_id, final String... ps)
//    {
//        return MyConvertUtil.ConvertToString(myPlusFunc.myInvokeLink(methodName, group_id, ps));
//    }

    /**
     * 联接函数的调用
     * */
//    public static Object myInvokeAllFuncScenes(final String methodName, final Long group_id, final Object... ps)
//    {
//        return myPlusFunc.myInvokeAllFuncScenes(methodName, group_id, ps);
//    }
    public static List myInvokeAllFuncScenes(final String methodName, final String user_token, final Object... ps)
    {
        return myPlusFunc.myInvokeAllFuncScenes(methodName, user_token, ps);
    }
//    public static String myInvokeAllFuncScenes(final String methodName, final String group_id, final String... ps)
//    {
//        return MyConvertUtil.ConvertToString(myPlusFunc.myInvokeAllFuncScenes(methodName, group_id, ps));
//    }

    public static String smartSql(byte[] userToken, byte[] sql) {
        //Object m_obj = MyCacheExUtil.restore(sql);
        //System.out.println(m_obj);
//        Object m_obj = MyCacheExUtil.restore(sql);
//        if (m_obj instanceof List) {
//            List rs = MySmartSql.reSmartSegmentLst((List) m_obj);
//            for (Object r : rs) {
//                System.out.println(r);
//            }
//        }
        return myPlusFunc.superSql(userToken, sql);
    }

    public static String showTrainData(final String cacheName, final Integer item_size) {
        List<double[]> lst = myPlusFunc.showTrainData(cacheName, item_size);

        StringBuilder sb = new StringBuilder();
        for (double[] m : lst)
        {
            sb.append(gson.toJson(m) + "\n");
        }
        return sb.toString();
        //return gson.toJson(lst);
    }

    public static void train_matrix_single(String dataset_name, String table_name, String value) {
        myPlusFunc.train_matrix_single(dataset_name, table_name, value);
    }

    public static String my_line_binary(byte[] bytes) {
        return (String)MyLineToBinary.restore(bytes);
    }

    public static Boolean hasConnPermission(String userToken) {
        return myPlusFunc.hasConnPermission(userToken);
    }
}
