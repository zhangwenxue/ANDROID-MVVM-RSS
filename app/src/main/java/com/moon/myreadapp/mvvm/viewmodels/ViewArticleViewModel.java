package com.moon.myreadapp.mvvm.viewmodels;

import android.app.Activity;
import android.databinding.Bindable;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.view.View;
import android.widget.Button;

import com.moon.appframework.action.RouterAction;
import com.moon.appframework.common.log.XLog;
import com.moon.appframework.common.util.CollectionUtils;
import com.moon.appframework.core.XApplication;
import com.moon.appframework.core.XDispatcher;
import com.moon.myreadapp.BR;
import com.moon.myreadapp.R;
import com.moon.myreadapp.common.adapter.ArticleRecAdapter;
import com.moon.myreadapp.common.components.pulltorefresh.PullToRefreshRecyclerView;
import com.moon.myreadapp.common.components.recyclerview.RecyclerItemClickListener;
import com.moon.myreadapp.common.components.toast.TastyToast;
import com.moon.myreadapp.common.components.toast.ToastHelper;
import com.moon.myreadapp.common.event.UpdateFeedEvent;
import com.moon.myreadapp.common.event.UpdateFeedListEvent;
import com.moon.myreadapp.common.event.UpdateUIEvent;
import com.moon.myreadapp.constants.Constants;
import com.moon.myreadapp.mvvm.models.dao.Article;
import com.moon.myreadapp.ui.ArticleActivity;
import com.moon.myreadapp.ui.ArticleWebActivity;
import com.moon.myreadapp.ui.LoginActivity;
import com.moon.myreadapp.ui.ViewArticleActivity;
import com.moon.myreadapp.util.BmobHelper;
import com.moon.myreadapp.util.BuiltConfig;
import com.moon.myreadapp.util.Conver;
import com.moon.myreadapp.util.DBHelper;
import com.moon.myreadapp.util.Globals;
import com.moon.myreadapp.util.PreferenceUtils;
import com.moon.myreadapp.util.VibratorHelper;
import com.rey.material.app.Dialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.bmob.v3.listener.SaveListener;

/**
 * Created by moon on 16/1/10.
 */
public class ViewArticleViewModel extends BaseViewModel {


    public enum Style {
        /**
         * 查看收藏
         */
        VIEW_FAVOR(R.string.view_artilce_title_favor),
        /**
         * 查看历史
         */
        VIEW_READ_HISTORY(R.string.view_artilce_title_history),
        /**
         * 查看全部未读
         */
        VIEW_UNREAD(R.string.view_artilce_title_unread);
        @StringRes
        int title;

        Style(@StringRes int t) {
            title = t;
        }

        public static Style find(int ordinal) {
            Style[] styles = values();
            for (int i = 0; i < styles.length; i++) {
                if (ordinal == styles[i].ordinal()) {
                    return styles[i];
                }
            }
            return VIEW_FAVOR;
        }

    }

    private ViewArticleActivity mView;

    private RecyclerItemClickListener articleClickListener;
    private ArticleRecAdapter mAdapter;

    private int currentPosition = -1;
    private Dialog mDialog;

    private Style mStyle;

    /**
     * 功能按钮文字
     */
    private String fucText;

    private boolean onPregress;

    public ViewArticleViewModel(ViewArticleActivity view, Style style) {
        this.mView = view;
        mStyle = style;
        initViews();
        initEvents();
    }

    @Override
    public void initViews() {
        mAdapter = new ArticleRecAdapter(mView, getBaseData(0, Constants.SINGLE_LOAD_SIZE), mStyle);
        mView.setTitle(mStyle.title);


        //初始化底部文字
        if (mStyle == Style.VIEW_FAVOR) {
            if (DBHelper.Query.getUser() != null) {
                setFucText(mView.getString(R.string.option_sync_storge_now));
            } else {
                setFucText(mView.getString(R.string.option_sync_storge_after_login));
            }
        } else if (mStyle == Style.VIEW_READ_HISTORY) {
            setFucText(mView.getString(R.string.option_delete_all_read_articles));
        } else if (mStyle == Style.VIEW_UNREAD) {
            setFucText(mView.getString(R.string.option_read_all));
        }
    }

    private List<Article> getBaseData(int start, int size) {
        if (mStyle == Style.VIEW_FAVOR) {
            //获取收藏数据
            return DBHelper.Query.getArticles(Article.Status.FAVOR, start, size);
        } else if (mStyle == Style.VIEW_READ_HISTORY) {
            //获取历史数据
            return DBHelper.Query.getArticlesReadHistory(start, size);
        } else if (mStyle == Style.VIEW_UNREAD) {
            //获取未读列表
            return DBHelper.Query.getArticlesUnRead(start, size);
        }
        return null;
    }


    @Override
    public void initEvents() {
        articleClickListener = new RecyclerItemClickListener(mView, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                readArticle(mAdapter.getItem(position), position);
                updateFeed();
                //打开原文还是链接
                boolean isOpenSource = PreferenceUtils.getInstance(mView).getBooleanParam(mView.getString(R.string.set_open_source_key, false));
                if (isOpenSource && mAdapter.getItem(position).getContainer().length() < Constants.MIN_CONTAINER_SIZE) {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.ARTICLE_TITLE, mAdapter.getItem(position).getTitle());
                    bundle.putString(Constants.ARTICLE_URL, mAdapter.getItem(position).getLink());
                    XDispatcher.from(mView).dispatch(new RouterAction(ArticleWebActivity.class, bundle, true));
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putLong(Constants.ARTICLE_ID, mAdapter.getItem(position).getId());
                    bundle.putInt(Constants.ARTICLE_POS, position);
                    XDispatcher.from(mView).dispatch(new RouterAction(ArticleActivity.class, bundle, true));
                }
            }

            @Override
            public void onItemLongClick(final View view, final int position) {
                //XLog.d("onItemLongClick execute!");
                final Article article = mAdapter.getmData().get(position);
                currentPosition = position;
                //震动
                VibratorHelper.shock(VibratorHelper.TIME.SHORT);
                View v = mView.getLayoutInflater().inflate(R.layout.menu_singer_article, null);

                mDialog = new Dialog(mView).
                        contentView(v).
                        cancelable(true).
                        layoutParams(-1, -2);
                mDialog.show();
                //已读
                v.findViewById(R.id.action_read).setVisibility(article.getUse_count() <= 0 ? View.VISIBLE : View.GONE);
                //menu.findItem(R.id.action_read).setVisible(article.getUse_count() <= 0);
                //收藏
                ((Button) (v.findViewById(R.id.action_read_favor))).setText(BuiltConfig.getString(article.getStatus() == Article.Status.FAVOR.status ? R.string.action_favor_back : R.string.action_favor));
                // menu.findItem(R.id.action_read_favor).setTitle(BuiltConfig.getString(article.getStatus() == Article.Status.FAVOR.status ? R.string.action_favor_back : R.string.action_favor));

            }

        });
    }

    public ArticleRecAdapter getmAdapter() {
        return mAdapter;
    }

    public RecyclerItemClickListener getArticleClickListener() {
        return articleClickListener;
    }

    @Override
    public void clear() {
        mView = null;
        mAdapter = null;
        articleClickListener = null;
    }


    /**
     * 刷新
     *
     * @param feedList
     */
    public void refresh(final PullToRefreshRecyclerView feedList) {
        feedList.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.setmData(getBaseData(0, Constants.SINGLE_LOAD_SIZE));
                feedList.getHeaderLoadingLayout().setLastUpdatedLabel(Conver.ConverToString(new Date(), "HH:mm"));
                //完成刷新
                feedList.onPullDownRefreshComplete();
            }
        });
    }

    /**
     * 加载更多
     */
    public boolean loadMore() {
        List<Article> loadData;
        if (mAdapter.getmData() == null || mAdapter.getmData().size() == 0) {
            loadData = getBaseData(0, Constants.SINGLE_LOAD_SIZE);
        } else {
            loadData = getBaseData(mAdapter.getmData().size(), Constants.SINGLE_LOAD_SIZE);
        }
        if (loadData != null) {
            mAdapter.addAll(loadData);
        }
        return !(loadData == null || loadData.size() < Constants.SINGLE_LOAD_SIZE);
    }


    private void readArticle(Article article, int position) {
        if (article == null) return;
        article.setLast_read_time(new Date());
        article.setUse_count(article.getUse_count() + 1);
        DBHelper.UpDate.saveArticle(article);
        mAdapter.notifyItemChanged(position);
    }

    private void updateFeed() {
        UpdateFeedEvent event = new UpdateFeedEvent(null, UpdateFeedEvent.TYPE.STATUS);
        event.setStatus(UpdateFeedEvent.NORMAL);
        XApplication.getInstance().bus.post(event);
    }

    @Bindable
    public String getFucText() {
        return fucText;
    }


    public void setFucText(String fucText) {
        this.fucText = fucText;
        notifyPropertyChanged(BR.fucText);
    }

    public void btnOnClick(View v) {

        if (currentPosition >= 0 || currentPosition < mAdapter.getmData().size()) {
            int id = v.getId();
            final Article article = mAdapter.getmData().get(currentPosition);
            switch (id) {
                case R.id.action_read:
                    readArticle(article, currentPosition);
                    getmAdapter().notifyItemChanged(getmAdapter().getWholePosition(currentPosition));
                    break;
                case R.id.action_read_favor:
                    //收藏
                    if (article.getStatus() == Article.Status.NORMAL.status) {
                        article.setStatus(Article.Status.FAVOR.status);
                        DBHelper.UpDate.saveArticle(article);
                        ToastHelper.showNotice(mView, BuiltConfig.getString(R.string.action_favor) + BuiltConfig.getString(R.string.success), TastyToast.STYLE_ALERT).setDuration(1000);
                    } else {
                        article.setStatus(Article.Status.NORMAL.status);
                        DBHelper.UpDate.saveArticle(article);
                        ToastHelper.showNotice(mView, BuiltConfig.getString(R.string.action_favor_back) + BuiltConfig.getString(R.string.success), TastyToast.STYLE_ALERT).setDuration(1000);
                    }
                    //通知更新
                    getmAdapter().notifyItemChanged(getmAdapter().getWholePosition(currentPosition));
                    break;
                case R.id.action_read_delete:
                    //删除
                    if (article.getStatus() == Article.Status.FAVOR.status){
                        //如果在收藏情况下被删除,且有objid,那么需要通知服务端
                        if (article.getObjectId() != null){
                            article.delete(mView);
                        }
                    }
                    mAdapter.remove(currentPosition);
                    article.setStatus(Article.Status.DELETE.status);
                    DBHelper.UpDate.saveArticle(article);
                    ToastHelper.showNotice(mView, BuiltConfig.getString(R.string.action_delete) + BuiltConfig.getString(R.string.success), TastyToast.STYLE_ALERT).setDuration(1000);
                    break;
            }

        }
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        fireUIEvent();
    }


    public void updateArticleByPosition(int pos, Article article) {
        if (mAdapter.getmData() == null) return;
        if (pos >= 0 && pos < mAdapter.getmData().size()) {
            mAdapter.getmData().set(pos, article);
            mAdapter.notifyItemChanged(mAdapter.getWholePosition(pos));
        }
    }

    /**
     * 底部按钮click
     *
     * @param view
     */
    public void OnFucBtnClick(final View view) {
        if (onPregress) return;
        if (mStyle == Style.VIEW_FAVOR) {
            if (DBHelper.Query.getUser() != null) {
                onPregress = true;
                setFucText(mView.getString(R.string.option_sync_storge_doing));
                BmobHelper.updateUserFavors(mView, -1, -1, new BmobHelper.SyncListener() {
                    @Override
                    public void onDatasSyncOver() {
                        onPregress = false;
                        mAdapter.setmData(getBaseData(0, Constants.SINGLE_LOAD_SIZE));
                        setFucText(Globals.getApplication().getString(R.string.option_sync_storge_last_time));
                    }

                    @Override
                    public void onDataSyncSuccess() {

                    }

                    @Override
                    public void onDataDownloadSuccess() {

                    }

                    @Override
                    public void onDataSyncFailure(int i, String s) {
                        onPregress = false;
                        setFucText(Globals.getApplication().getString(R.string.option_sync_storge_fial));
                    }

                    @Override
                    public void onDataDownloadFailure(int i, String s) {
                        onPregress = false;
                        setFucText(Globals.getApplication().getString(R.string.option_sync_storge_fial));
                    }
                });


            } else {
                XDispatcher.from(mView).dispatch(new RouterAction(LoginActivity.class, null, true));
            }
        } else if (mStyle == Style.VIEW_READ_HISTORY) {
            //查看历史记录
            onPregress = true;
            setFucText(mView.getString(R.string.option_delete_all_read_articles_doing));
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    DBHelper.Delete.deleteArticleReadAndUnFavor();
                    mAdapter.setmData(getBaseData(0, Constants.SINGLE_LOAD_SIZE));
                    onPregress = false;
                    setFucText(null);
                    ToastHelper.showToast(R.string.set_clean_cache_summary_down);
                }
            }, 200);
        } else if (mStyle == Style.VIEW_UNREAD) {
            //查看未读
            onPregress = true;
            setFucText("操作中..");
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (view.getTag() == null){
                        List<Article> articles = DBHelper.UpDate.getAllUnReadArticles();
                        if (articles == null || articles.size() ==0){
                            setFucText(null);
                            ToastHelper.showToast(R.string.option_read_all_none);
                        } else {
                            setFucText(mView.getString(R.string.option_read_all_go_on, articles.size()));
                            view.setTag(articles);
                        }
                    } else {
                        List<Article> articles = (List<Article>)view.getTag();
                        DBHelper.UpDate.readAllArticles(articles);
                        setFucText(null);
                        view.setTag(null);

                        ToastHelper.showToast(R.string.option_read_all_down,articles.size());
                        //更改视图数据
                        mAdapter.setmData(getBaseData(0, Constants.SINGLE_LOAD_SIZE));
                        //消息通知
                        XApplication.getInstance().bus.post(new UpdateFeedListEvent());
                    }
                    onPregress = false;
                }
            }, 200);
        }
        fireUIEvent();
    }

    private void fireUIEvent() {
        UpdateUIEvent event = new UpdateUIEvent(UpdateUIEvent.ARTIVIE_COUNT_CHANGE);
        XApplication.getInstance().bus.post(event);
    }
}
