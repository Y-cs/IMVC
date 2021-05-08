package self.ibeans.util;

import java.sql.SQLException;

/**
 * @author 应癫
 * <p>
 * 事务管理器类：负责手动事务的开启、提交、回滚
 */
public interface TransactionManager {


    // 开启手动事务控制
    public void beginTransaction() throws SQLException;


    // 提交事务
    public void commit() throws SQLException;


    // 回滚事务
    public void rollback() throws SQLException;
}
