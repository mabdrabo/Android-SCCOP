package io.github.mabdrabo.sccop;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Created by mahmoud on 1/1/14.
 */
public class GridItemAdapter extends BaseAdapter {

    private Context context;
    private String[] names;
    private String[] values;
    private String[] units;

    public GridItemAdapter(Context context, String[] names, String[] values, String[] units) {
        this.context = context;
        this.names = names;
        this.values = values;
        this.units = units;
    }

    @Override
    public int getCount() {
        return this.names.length;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View gridView;

        if (convertView == null) {

            gridView = new View(context);
            gridView = inflater.inflate(R.layout.grid_item, null);
            ((TextView) gridView
                    .findViewById(R.id.nameTextView)).setText(this.names[position]);
            ((TextView) gridView
                    .findViewById(R.id.valueTextView)).setText(this.values[position]);
            ((TextView) gridView
                    .findViewById(R.id.unitTextView)).setText(this.units[position]);
        } else {
            gridView = (View) convertView;
        }

        return gridView;
    }
}
