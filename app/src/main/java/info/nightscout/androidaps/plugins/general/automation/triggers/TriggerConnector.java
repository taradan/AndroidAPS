package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.AutomationFragment;
import info.nightscout.androidaps.plugins.general.automation.dialogs.ChooseTriggerDialog;
import info.nightscout.utils.JsonHelper;

public class TriggerConnector extends Trigger {
    public enum Type {
        AND,
        OR,
        XOR;

        public boolean apply(boolean a, boolean b) {
            switch (this) {
                case AND:
                    return a && b;
                case OR:
                    return a || b;
                case XOR:
                    return a ^ b;
            }
            return false;
        }

        public @StringRes int getStringRes() {
            switch (this) {
                case OR:
                    return R.string.or;
                case XOR:
                    return R.string.xor;

                default:
                case AND:
                    return R.string.and;
            }
        }

        public static List<String> labels() {
            List<String> list = new ArrayList<>();
            for(Type t : values()) {
                list.add(MainApp.gs(t.getStringRes()));
            }
            return list;
        }
    }

    protected List<Trigger> list = new ArrayList<>();
    private Type connectorType;

    public TriggerConnector() {
        connectorType = Type.AND;
    }

    public TriggerConnector(Type connectorType) {
        this.connectorType = connectorType;
    }

    public void changeConnectorType(Type type) { this.connectorType = type; }

    public Type getConnectorType() { return connectorType; }

    public synchronized void add(Trigger t) {
        list.add(t);
        t.connector = this;
    }

    public synchronized boolean remove(Trigger t) {
        return list.remove(t);
    }

    public int size() {
        return list.size();
    }

    public Trigger get(int i) {
        return list.get(i);
    }

    public int pos(Trigger trigger) {
        for(int i = 0; i < list.size(); ++i) {
            if (list.get(i) == trigger) return i;
        }
        return -1;
    }

    @Override
    public synchronized boolean shouldRun() {
        boolean result = true;

        // check first trigger
        if (list.size() > 0)
            result = list.get(0).shouldRun();

        // check all others
        for (int i = 1; i < list.size(); ++i) {
            result = connectorType.apply(result, list.get(i).shouldRun());
        }

        return result;
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerConnector.class.getName());
            JSONObject data = new JSONObject();
            data.put("connectorType", connectorType.toString());
            JSONArray array = new JSONArray();
            for (Trigger t : list) {
                array.put(t.toJSON());
            }
            data.put("triggerList", array);
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            connectorType = Type.valueOf(JsonHelper.safeGetString(d, "connectorType"));
            JSONArray array = d.getJSONArray("triggerList");
            for (int i = 0; i < array.length(); i++) {
                Trigger newItem = instantiate(new JSONObject(array.getString(i)));
                add(newItem);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return connectorType.getStringRes();
    }

    @Override
    public String friendlyDescription() {
        int counter = 0;
        StringBuilder result = new StringBuilder();
        for (Trigger t : list) {
            if (counter++ > 0) result.append(friendlyName());
            result.append(t.friendlyDescription());
        }
        return result.toString();
    }

    private AutomationFragment.TriggerListAdapter adapter;

    public void rebuildView() {
        if (adapter != null)
            adapter.rebuild();
    }

    @Override
    public View createView(Context context) {
        final int padding = MainApp.dpToPx(5);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(padding,padding,padding,padding);
        root.setBackgroundResource(R.drawable.border_automation_unit);

        LinearLayout triggerListLayout = new LinearLayout(context);
        triggerListLayout.setOrientation(LinearLayout.VERTICAL);
        triggerListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(triggerListLayout);

        adapter = new AutomationFragment.TriggerListAdapter(context, triggerListLayout, list);

        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(buttonLayout);

        Button buttonRemove = new Button(context);
        buttonRemove.setText("-");
        buttonRemove.setOnClickListener(v -> {
            if (connector != null) {
                connector.remove(TriggerConnector.this);
                connector.simplify();
                connector.adapter.rebuild();
            } else {
                // no parent
                list.clear();
                simplify();
                adapter.rebuild();
            }
        });
        buttonLayout.addView(buttonRemove);

        Button buttonAdd = new Button(context);
        buttonAdd.setText("+");
        buttonAdd.setOnClickListener(v -> {
            ChooseTriggerDialog dialog = ChooseTriggerDialog.newInstance();
            FragmentManager manager = AutomationFragment.fragmentManager();
            dialog.show(manager, "ChooseTriggerDialog");
            dialog.setOnClickListener(newTriggerObject -> addTrigger(adapter, newTriggerObject, getConnectorType()));
        });
        buttonLayout.addView(buttonAdd);

        return root;
    }

    private void addTrigger(AutomationFragment.TriggerListAdapter adapter, Trigger trigger, Type connection) {
        if (getConnectorType().equals(connection)) {
            add(trigger);
        } else {
            TriggerConnector t = new TriggerConnector(connection);
            t.add(trigger);
            add(t);
        }
        adapter.rebuild();
    }

    public TriggerConnector simplify() {
        // simplify children
        for(int i = 0; i < size(); ++i) {
            if (get(i) instanceof TriggerConnector) {
                TriggerConnector t = (TriggerConnector) get(i);
                t.simplify();
            }
        }

        // drop connector with only 1 element
        if (size() == 1 && get(0) instanceof TriggerConnector) {
            TriggerConnector c = (TriggerConnector) get(0);
            remove(c);
            changeConnectorType(c.getConnectorType());
            for (Trigger t : c.list) {
                add(t);
            }
            c.list.clear();
            return simplify();
        }

        // merge connectors
        if (connector != null && (connector.getConnectorType().equals(connectorType) || size() == 1)) {
            connector.remove(this);
            for (Trigger t : list) {
                connector.add(t);
            }
            list.clear();
            return connector.simplify();
        }

        return this;
    }

}
