define(['jquery', 'bootstrap', "logging"], function ($) {
    var overview = overview || {};

    overview = {
        init: function () {
            this.navigation.init();
            this.bootstrap.init();
            this.stages.init();
            this.feedback.init();
        },

        bootstrap: {
            activeFilter: "all",
            init: function () {
                $('#usr-settings-theme').bind('change', function () {
                    $('#css').attr('href', $(this).val());
                });

                $('#search').bind('keyup', function () {
                    overview.stages.filter($(this).val());
                });
                $('#btn-refresh').bind('click', function () {
                    overview.stages.reload();
                });
                $('.modal').on('hide.bs.modal', function () {
                    $(".shell").html("");
                    overview.stages.reload();
                });
                $('[data-toggle="tab"]').on('click', function () {
                    var stages, filter, check;
                    stages = $("#all-stages").find("tr");
                    filter = $(this).attr("href").slice(1);
                    check = '';

                    overview.bootstrap.activeFilter = filter;

                    if (filter == 'all') {
                        stages.removeClass('filter');
                    } else {
                        stages.addClass('filter');
                    }

                    if (parseInt($(this).find('.badge').html()) == 0) {
                        $('#empty').removeClass('hidden');
                    } else {
                        $('#empty').addClass('hidden');
                    }

                    stages.each(function () {

                        if ($(this).attr('data-extractionurl') != null) {

                            if (filter === "trunks") {
                                check = '/trunk';
                            } else if (filter === "branches") {
                                check = '/branches/';
                            } else if (filter === "workspaces") {
                                check = '/workspaces/';
                            }

                            $(this).addClass('hidden');

                            if (filter === 'all' || filter === '') {
                                $(this).removeClass('hidden');
                            } else {
                                if ($(this).attr('data-extractionurl').indexOf(check) > -1) {
                                    $(this).removeClass('hidden');
                                }
                            }

                        }

                    });

                    overview.stages.filter($('#search').val());

                });

            }
        },

        navigation: {
            init: function () {
                var hash;
                hash = location.hash.slice(1);
                if (hash.indexOf(':') > -1) {
                    params = hash.split(':');
                    hash = params[0];
                }
                this.hashChange()
                this.loadContentByHash(hash != '' ? hash : 'dashboard');


            },

            hashChange: function () {
                $(window).bind('hashchange', function () {
                    var h, params;
                    h = location.hash.slice(1);
                    params = null;
                    if (h.indexOf(':') > -1) {
                        params = h.split(':');
                        h = params[0];
                    }
                    if (h != '') {
                        overview.navigation.loadContentByHash(h, params);
                    }
                    overview.navigation.checkLocationHash();
                });
            },

            loadContentByHash: function (h) {

                if ($('div').find('.content.' + h).length == 0) {

                    $('.content').addClass('hidden');
                    $('.tab').removeClass('active');
                    $('.content.error').removeClass('hidden');

                } else {

                    $('.content').each(function () {
                        $(this).addClass('hidden');
                        if ($(this).hasClass(h)) {
                            $(this).removeClass('hidden');
                            if ($(this).attr('data-title') != '') {
                                document.title = 'Stage  ' + $(this).attr('data-title');
                            }
                        }
                    });

                    $('.tab').each(function () {
                        $(this).removeClass('active');
                        if ($(this).hasClass(h)) {
                            $(this).addClass('active');
                        }
                    });

                }

            },
            checkLocationHash: function () {

                var hash, i;
                hash = window.location.hash.slice(1);

                if (hash.indexOf(':') > -1) {
                    parameters = hash.split(':');
                    for (i = 1; i < parameters.length; i += 1) {
                        parameter = parameters[i];
                        if (parameter.indexOf('=') > -1) {
                            param = parameter.split('=');
                            if (param[0] == 'search') {
                                overview.stages.filter(param[1]);
                            } else if (param[0] == 'stage') {
                                overview.navigation.updateStageBreadcrump(param[1]);
                            }
                        }
                    }
                }

            }
        },

        updateStageBreadcrump: function (stagename) {
            $('.breadcrumb .stagename').html(stagename);
        },

        info: {
            show: function (type, title, text) {
                $('#info').append($('<div />').attr('class', 'alert alert-dismissable alert-' + type).html('<button type="button" class="close" data-dismiss="alert">X</button><h5>' + title + '</h5>' + text).fadeIn().delay(2000).fadeOut());
            },
            text: function (element, text) {
                $(element).html(text).removeClass('hidden');

            }
        },

        stages: {
            init: function () {
                overview.stages.reload();
                $('[data-action]').on('click', overview.stages.action);
                $('#loading').remove();
                overview.stages.silentReload();
            },


            silentReload: function () {

                overview.stages.reload();
                setTimeout(function () {
                    overview.stages.silentReload();
                }, overview.interval.withCounter(0));
            },

            recountTabs: function (wrapper) {
                var trunks, branches, workspaces;

                trunks = wrapper.find(".trunk").size();
                branches = wrapper.find(".branch").size()
                workspaces = wrapper.find(".workspace").size();
                $('#all-count').html(trunks + workspaces + branches);

                $('#trunks-count').html(trunks);
                $('#branches-count').html(branches);
                $('#workspaces-count').html(workspaces);
            },

            reload: function () {


                $.ajax('/stages', {
                    dataType: "html",
                    success: function (data) {

                        var wrapper;
                        wrapper = $('#all-stages');

                        $(data).each(function (i, tr) {
                            var oldTr;
                            var actions;
                            var id;

                            id = tr.id;

                            if (id !== undefined) {
                                id = id.replace(/\./g, "\\.");
                            }
                            oldTr = wrapper.find('#' + id);

                            if (oldTr.length === 0) {
                                wrapper.append(tr);
                                actions = $('[data-action]');
                                actions.off('click', overview.stages.action)
                                actions.on('click', overview.stages.action);
                            } else if (oldTr.attr("data-hash") !== $(tr).attr("data-hash")) {
                                oldTr.replaceWith(tr);
                            }
                        });
                        $('[data-toggle="popover"]').popover();
                        overview.stages.recountTabs(wrapper);
                        overview.navigation.checkLocationHash();
                    }

                });
            },

            action: function () {
                var stage, action, option, timeout, estimate, p, i, url;
                if ($(this).parent().hasClass('disabled') && $(this).attr('data-stage') === null) {
                    return false;
                }

                stage = $(this).attr('data-stage');
                action = $(this).attr('data-action');
                option = $(this).attr('data-option');
                timeout = $(this).attr('data-timeout');
                estimate = $(this).attr('data-estimate');
                p = $(this).parent().parent();
                i = $(p).find('.info');

                url = "/stages/" + stage + "/" + action;
                $('#' + stage + ' a.action').addClass('disabled');

                $(p).find('.fa').removeClass('hidden');
                $(p).find('.label').attr('class', 'label label-primary').html('n/a');

                //setInfoText(i, $(this).attr('data-title'));
                //showInfo('info', stage, $(this).attr('data-title'));

                if (option != null) {
                    url = url + "/-" + option;
                }

                if (timeout === undefined) {
                    timeout = 10;
                }
                $('.modal-title').text("Console output");
                $('.modal').modal();
                $.post(url).fail(function (r) {
                    $(p).find('.fa').addClass('hidden');
                    $(p).find('.label').attr('class', 'label label-warning').html('broken');
                    $(p).addClass('warning');
                    overview.info.show(i, 'dude something went wrong ... try again or send us a <a href="#feedback">feedback</a>');
                    overview.info.text('warning', stage, '%ERROR_MESSAGE%');
                    log.error(r);
                }).done(function (r) {
                    var spinner;
                    $('.modal-body .shell').html("").show();
                    spinner = '.fa-spinner';
                    $(spinner).show();
                    overview.stages.showLog('.modal-body .shell', r, 0, spinner, '.modal', estimate);

                });


                return false;

            },
            showLog: function (element, id, index, spinner, parent, estimate) {
                overview.stages.fetchLog(element, id, index, spinner, parent);

                //overview.stages.progressbar.updater(".modal-footer", estimate, new Date())
            },

            progressbar: {
                updater: function (parent, estimate, start) {
                    var done, percent, text;
                    done = new Date() - start;
                    percent = Math.round(done / estimate * 100);
                    text = "ETA " + Math.round(estimate / 1000 - done / 1000) + "sec.";
                    overview.stages.progressbar.update(parent, percent, text)
                    if (percent <= 100) {
                        setTimeout(function () {
                            overview.stages.progressbar.updater(parent, estimate, start);
                        }, 200);
                    }

                },
                update: function (parent, percent, text) {
                    parent = $(parent);
                    if (parent.find(".logprogress").size() > 0) {
                        var element;
                        element = parent.find('.progress-bar');
                        element.attr('aria-valuenow', percent);
                        element.attr('style', "width:" + percent + "%");
                        element.text(text)
                    } else {
                        parent.append('<div class="progress logprogress">' +
                            '<div class="progress-bar" role="progressbar" aria-valuenow="' + percent + '" aria-valuemin="0" aria-valuemax="100" style="width: ' + percent + '%;">' +
                            text + '</div></div>');
                    }
                },
            },
            fetchLog: function (element, id, index, spinner, parent) {
                $.get("/processes/" + id + "/log", {"index": index}).done(function (data, status, response) {
                    if (response.getResponseHeader("X-running") === null) {
                        $(spinner).hide();
                    } else {
                        setTimeout(function () {
                            overview.stages.fetchLog(element, id, index, spinner, parent);
                        }, 1000);
                    }
                });

            },

            filter: function (search) {
                if (search !== '') {
                    var activeFilter;
                    activeFilter = overview.bootstrap.activeFilter;
                    document.location.hash = '#dashboard:search=' + search;

                    $('#all-stages').find('tr.stage').addClass('hidden');
                    $('#empty').addClass('hidden');

                    found = 0;

                    $('#all-stages').find('tr.stage' + (activeFilter == 'all' ? '' : '.' + activeFilter)).each(function () {
                        if ($(this).attr('data-extractionurl').indexOf(search) > -1 || $(this).attr('data-user').indexOf(search) > -1 || $(this).find('td.name span').html().indexOf(search) > -1) {
                            $(this).removeClass('hidden');
                            found++;
                        }
                    });

                    if (found === 0) {
                        $('#empty').removeClass('hidden');
                    }

                } else {
                    document.location.hash = '#dashboard';
                    $('#empty').addClass('hidden');
                    $('#all-stages').find('tr.stage' + (activeFilter == 'all' ? '' : '.' + activeFilter)).removeClass('hidden');
                }

            }
        },
        feedback: {
            init: function () {
                $('#feedback-submit').bind('click', function (e) {
                    e.preventDefault();
                    var text = $('#feedback-text');
                    if (text.val() != "") {
                        $.post("/feedback", { message: text.val()})
                            .done(function () {
                                text.parent().parent().prepend("<div class=\"alert alert-success\">Thanks.</div>");
                                text.val("");
                            })
                            .fail(function () {
                                text.parent().parent()
                                    .prepend("<div class=\"alert alert-danger\">Opps, something wen't wrong =(.</div>");
                            });
                    }
                });
            }
        },


        interval: {
            withCounter: function (count) {
                return 5000;
            }
        }

    };
    overview.init();
});