package com.mz.jarboot.base;

import java.util.Comparator;

/**
 * @author majianzheng
 */
public class RequestMappingInfo {
    
    private PathRequestCondition pathRequestCondition;
    
    private ParamRequestCondition paramRequestCondition;
    
    public ParamRequestCondition getParamRequestCondition() {
        return paramRequestCondition;
    }
    
    public void setParamRequestCondition(ParamRequestCondition paramRequestCondition) {
        this.paramRequestCondition = paramRequestCondition;
    }
    
    public void setPathRequestCondition(PathRequestCondition pathRequestCondition) {
        this.pathRequestCondition = pathRequestCondition;
    }
    
    @Override
    public String toString() {
        return "RequestMappingInfo{" + "pathRequestCondition=" + pathRequestCondition + ", paramRequestCondition="
                + paramRequestCondition + '}';
    }
    
    public static class RequestMappingInfoComparator implements Comparator<RequestMappingInfo> {
        
        @Override
        public int compare(RequestMappingInfo o1, RequestMappingInfo o2) {
            return Integer.compare(o2.getParamRequestCondition().getExpressions().size(),
                    o1.getParamRequestCondition().getExpressions().size());
        }
    }
}
