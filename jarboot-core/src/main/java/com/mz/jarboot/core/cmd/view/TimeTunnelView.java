package com.mz.jarboot.core.cmd.view;

import com.mz.jarboot.core.cmd.impl.TimeTunnelTable;
import com.mz.jarboot.core.cmd.model.TimeFragmentVO;
import com.mz.jarboot.core.cmd.model.TimeTunnelModel;
import com.mz.jarboot.core.cmd.view.element.Element;
import com.mz.jarboot.core.cmd.view.element.TableElement;
import com.mz.jarboot.common.utils.StringUtils;

/**
 * Term view for TimeTunnelCommand
 * @author majianzheng
 */
public class TimeTunnelView implements ResultView<TimeTunnelModel> {

    @Override
    public String render(TimeTunnelModel timeTunnelModel) {
        Integer expand = timeTunnelModel.getExpand();
        boolean isNeedExpand = isNeedExpand(expand);
        Integer sizeLimit = timeTunnelModel.getSizeLimit();
        StringBuilder sb = new StringBuilder();

        if (timeTunnelModel.getTimeFragmentList() != null) {
            //show list table: tt -l / tt -t
            Element table = TimeTunnelTable
                    .drawTimeTunnelTable(timeTunnelModel.getTimeFragmentList(), timeTunnelModel.getFirst());
            sb.append(table.toHtml());

        } else if (timeTunnelModel.getTimeFragment() != null) {
            //show detail of single TimeFragment: tt -i 1000
            TimeFragmentVO tf = timeTunnelModel.getTimeFragment();
            TableElement table = TimeTunnelTable.createDefaultTable();
            TimeTunnelTable.drawTimeTunnel(table, tf);
            TimeTunnelTable.drawParameters(table, tf.getParams(), isNeedExpand, expand);
            TimeTunnelTable.drawReturnObj(table, tf, isNeedExpand, expand, sizeLimit);
            TimeTunnelTable.drawThrowException(table, tf, isNeedExpand, expand);
            sb.append(table.toHtml());

        } else if (timeTunnelModel.getWatchValue() != null) {
            //watch single TimeFragment: tt -i 1000 -w 'params'
            Object value = timeTunnelModel.getWatchValue();
            if (isNeedExpand) {
                sb.append(new ObjectView(value, expand, sizeLimit).draw()).append(StringUtils.LF);
            } else {
                sb.append(StringUtils.objectToString(value)).append(StringUtils.LF);
            }

        } else if (timeTunnelModel.getWatchResults() != null) {
            //search & watch: tt -s 'returnObj!=null' -w 'returnObj'
            TableElement table = TimeTunnelTable.createDefaultTable();
            TimeTunnelTable.drawWatchTableHeader(table);
            TimeTunnelTable.drawWatchResults(table, timeTunnelModel.getWatchResults(), isNeedExpand, expand, sizeLimit);
            sb.append(table.toHtml());

        } else if (timeTunnelModel.getReplayResult() != null) {
            //replay: tt -i 1000 -p
            TimeFragmentVO replayResult = timeTunnelModel.getReplayResult();
            Integer replayNo = timeTunnelModel.getReplayNo();
            TableElement table = TimeTunnelTable.createDefaultTable();
            TimeTunnelTable.drawPlayHeader(replayResult.getClassName(),
                    replayResult.getMethodName(), replayResult.getObject(), replayResult.getIndex(), table);
            TimeTunnelTable.drawParameters(table, replayResult.getParams(), isNeedExpand, expand);
            if (replayResult.isReturn()) {
                TimeTunnelTable.drawPlayResult(table, replayResult.getReturnObj(),
                        isNeedExpand, expand, sizeLimit, replayResult.getCost());
            } else {
                TimeTunnelTable.drawPlayException(table, replayResult.getThrowExp(), isNeedExpand, expand);
            }
            sb
                    .append(table.toHtml())
                    .append(String.format("Time fragment[%d] successfully replayed %d times.",
                            replayResult.getIndex(), replayNo))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private boolean isNeedExpand(Integer expand) {
        return null != expand && expand > 0;
    }

}
