package com.group15.studydesk;

import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import com.group15.studydesk.databinding.ItemRecordBinding;
import java.util.*;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {
  public record Card(
      Row row,
      String tag,
      String title,
      String detail,
      String primary,
      Runnable action,
      double[] chart) {
    public Card(Row row, String tag, String title, String detail, String primary, Runnable action) {
      this(row, tag, title, detail, primary, action, null);
    }
  }

  public interface Actions {
    void primary(Row row);

    void edit(Row row);

    void more(Row row);
  }

  private List<Card> cards = new ArrayList<>();
  private final Actions actions;

  public CardAdapter(Actions a) {
    actions = a;
  }

  public void submit(List<Card> next) {
    cards = next;
    notifyDataSetChanged();
  }

  @Override
  public Holder onCreateViewHolder(ViewGroup p, int type) {
    return new Holder(ItemRecordBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
  }

  @Override
  public void onBindViewHolder(Holder h, int pos) {
    Card c = cards.get(pos);
    ItemRecordBinding b = h.b;
    b.chart.show(c.chart(), c.tag().equals("STUDY HEATMAP"));
    b.tag.setText(Language.translate(b.getRoot().getContext(), c.tag()));
    b.tag.setTextColor(
        androidx.core.content.ContextCompat.getColor(b.getRoot().getContext(), R.color.accent));
    if (c.row() != null && c.row().kind.equals("course")) {
      try {
        b.tag.setTextColor(android.graphics.Color.parseColor(c.row().s("color")));
      } catch (Exception ignored) {
      }
    }
    b.title.setText(c.title());
    b.detail.setText(c.detail());
    b.primary.setText(Language.translate(b.getRoot().getContext(), c.primary()));
    boolean read = c.row() == null;
    b.edit.setVisibility(read ? View.GONE : View.VISIBLE);
    b.more.setVisibility(read ? View.GONE : View.VISIBLE);
    b.primary.setVisibility(read && c.action() == null ? View.GONE : View.VISIBLE);
    b.primary.setOnClickListener(
        v -> {
          if (c.action() != null) c.action().run();
          else if (c.row() != null) actions.primary(c.row());
        });
    b.edit.setOnClickListener(v -> actions.edit(c.row()));
    b.more.setOnClickListener(v -> actions.more(c.row()));
    b.getRoot()
        .setOnClickListener(
            v -> {
              if (c.action() != null) c.action().run();
              else if (c.row() != null) actions.primary(c.row());
            });
  }

  @Override
  public int getItemCount() {
    return cards.size();
  }

  static class Holder extends RecyclerView.ViewHolder {
    ItemRecordBinding b;

    Holder(ItemRecordBinding b) {
      super(b.getRoot());
      this.b = b;
    }
  }
}
