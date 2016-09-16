package com.fraz.dartlog.game;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import com.fraz.dartlog.R;
import com.fraz.dartlog.game.x01.X01;
import com.fraz.dartlog.game.x01.X01PlayerData;

import java.util.LinkedList;

public class GameListAdapter extends BaseExpandableListAdapter {

    private X01 game;
    private Activity context;

    public GameListAdapter(Activity context, X01 game) {
        this.context = context;
        this.game = game;
    }

    @Override
    public int getGroupCount() {
        return game.getNumberOfPlayers();
    }

    @Override
    public int getChildrenCount(int i) {
        return 1;
    }

    @Override
    public Object getGroup(int i) {
        return game.getPlayer(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return game.getPlayer(i);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i * 100 + i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int position, boolean isExpanded, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View listItem = view;
        X01PlayerData player = (X01PlayerData) game.getPlayer(position);

        if (view == null) {
            listItem = inflater.inflate(R.layout.game_player_list_item, parent, false);
        }

        TextView playerNameView = (TextView) listItem.findViewById(R.id.game_player_list_item_name);
        playerNameView.setText(player.getPlayerName());

        TextView scoreView = (TextView) listItem.findViewById(R.id.game_player_list_item_score);
        scoreView.setText(String.valueOf(player.getScore()));

        setBackgroundColor(player, position, listItem.findViewById(R.id.game_player_list_item));
        return listItem;
    }

    @Override
    public View getChildView(int position, int childPosition, boolean isLastChild,
                             View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View listItem = view;

        if (view == null) {
            listItem = inflater.inflate(R.layout.game_player_child_list_item, parent, false);
        }
        X01PlayerData player = (X01PlayerData) game.getPlayer(position);

        // Set total score history text
        LinkedList<Integer> scores = new LinkedList<>(player.getTotalScoreHistory());
        scores.addLast(player.getScore());
        String totalScoreHistoryText = createScoresString(scores);
        ((TextView) listItem.findViewById(R.id.total_score_history)).setText(totalScoreHistoryText);

        //Set average and max score text
        TextView avgScore = (TextView) listItem.findViewById(R.id.average_score);
        TextView maxScore = (TextView) listItem.findViewById(R.id.max_score);
        avgScore.setText(String.format("%.1f", player.getAvgScore()));
        maxScore.setText(String.valueOf(player.getMaxScore()));

        //Set checkout text
        TextView checkout = (TextView) listItem.findViewById(R.id.checkout);
        checkout.setText(game.getCheckoutText(player));

        setBackgroundColor(player, position, listItem.findViewById(R.id.game_player_child_list_item));
        return listItem;
    }

    private String createScoresString(LinkedList<Integer> scores) {
        String scoreHistoryText = "";
        for (Integer score : scores) {
            scoreHistoryText += String.format("%s ", Integer.toString(score));
        }
        return scoreHistoryText.trim();
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return false;
    }

    private void setBackgroundColor(X01PlayerData player, int position, View listItem) {
        if (player.getScore() == 0) {
            listItem.setBackgroundResource(R.color.game_player_winner);
        } else if (game.getCurrentPlayerIdx() == position) {
            listItem.setBackgroundResource(R.color.light_grey);
        } else {
            listItem.setBackgroundResource(R.color.main_white);
        }
    }

}