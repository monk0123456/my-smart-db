package cn.myservice;

import cn.smart.service.IMyInsertKv;

public class MyInsertKvService {
    private IMyInsertKv myInsertKv;

    private static class InstanceHolder {
        public static MyInsertKvService instance;

        static {
            try {
                instance = new MyInsertKvService();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取单例模式
     * */
    public static MyInsertKvService getInstance() {
        return MyInsertKvService.InstanceHolder.instance;
    }

    /**
     * 构造函数设置为私有，只能通过 getInstance() 方法获取
     * */
    private MyInsertKvService() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Class<?> cls = Class.forName("org.gridgain.plus.dml.MySmartDb");
        myInsertKv = (IMyInsertKv) cls.newInstance();
    }

    public IMyInsertKv getMyInsertKv() {
        return myInsertKv;
    }
}
