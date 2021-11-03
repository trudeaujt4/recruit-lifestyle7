package com.mz.jarboot.core.cmd.view;

import com.mz.jarboot.core.cmd.model.OgnlModel;
import com.mz.jarboot.core.constant.CoreConstant;
import com.mz.jarboot.core.utils.StringUtils;

/**
 * Term view of OgnlCommand
 * @author majianzheng
 */
public class OgnlView implements ResultView<OgnlModel> {
    @Override
    public String render(OgnlModel model) {
        StringBuilder sb = new StringBuilder();
        if (model.getMatchedClassLoaders() != null) {
            sb.append("Matched classloaders: \n");
            ClassLoaderView.drawClassLoaders(sb, model.getMatchedClassLoaders(), false);
            sb.append(CoreConstant.BR);
            return sb.toString();
        }

        int expand = model.getExpand();
        Object value = model.getValue();
        String resultStr = StringUtils.objectToString(expand >= 0 ? new ObjectView(value, expand).draw() : value);
        sb.append(resultStr).append(CoreConstant.BR);
        return sb.toString();
    }
}
