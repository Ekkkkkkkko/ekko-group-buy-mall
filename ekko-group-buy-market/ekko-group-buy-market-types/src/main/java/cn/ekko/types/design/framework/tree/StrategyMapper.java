package cn.ekko.types.design.framework.tree;

/**
 * 根据请求和上下文选择下一段策略。
 */
public interface StrategyMapper<T, D, R> {

    StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext) throws Exception;

}
